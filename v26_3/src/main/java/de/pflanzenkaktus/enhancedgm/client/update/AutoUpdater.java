package de.pflanzenkaktus.enhancedgm.client.update;

import de.pflanzenkaktus.enhancedgm.feature.UpdateChecker;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * 1-Click In-Game Auto-Updater for enhancedGM.
 * Downloads GitHub release assets securely and stages them for the next Minecraft launch,
 * bypassing Windows file locks via JVM shutdown hooks and background swap commands.
 */
public final class AutoUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger("enhancedGM/AutoUpdater");
    private static final AtomicBoolean DOWNLOADING = new AtomicBoolean(false);
    private static volatile boolean updateScheduledOnExit = false;

    private AutoUpdater() {}

    public static boolean isDownloading() {
        return DOWNLOADING.get();
    }

    public static boolean isUpdateScheduledOnExit() {
        return updateScheduledOnExit;
    }

    /**
     * Resolves the Minecraft mods directory.
     */
    public static Path getModsDirectory() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path mods = gameDir.resolve("mods");
        try {
            Files.createDirectories(mods);
        } catch (Throwable ignored) {}
        return mods;
    }

    /**
     * Finds the currently loaded mod jar on disk.
     */
    public static Path getCurrentJarPath() {
        try {
            ModContainer container = FabricLoader.getInstance().getModContainer("gm-addon").orElse(null);
            if (container != null && !container.getOrigin().getPaths().isEmpty()) {
                for (Path p : container.getOrigin().getPaths()) {
                    if (p != null && Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar")) {
                        return p.toAbsolutePath().normalize();
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Could not determine current jar path via FabricLoader origin: {}", t.getMessage());
        }

        // Fallback: search mods directory for current mod jar
        Path mods = getModsDirectory();
        if (Files.isDirectory(mods)) {
            try (var stream = Files.list(mods)) {
                return stream
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return (name.startsWith("enhancedgm-") || name.startsWith("gm-addon-"))
                            && name.endsWith(".jar")
                            && !name.endsWith(".pending");
                    })
                    .findFirst()
                    .map(p -> p.toAbsolutePath().normalize())
                    .orElse(null);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Clean up leftover .pending files or previous versions on game startup.
     */
    public static void cleanupPendingUpdates() {
        try {
            Path mods = getModsDirectory();
            if (!Files.isDirectory(mods)) return;

            try (var stream = Files.list(mods)) {
                stream.forEach(file -> {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".pending")) {
                        String cleanName = name.substring(0, name.length() - ".pending".length());
                        Path target = mods.resolve(cleanName);
                        try {
                            if (!Files.exists(target) && isValidZip(file)) {
                                Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
                                LOGGER.info("Promoted pending update file to {}", target.getFileName());
                            } else {
                                Files.deleteIfExists(file);
                            }
                        } catch (Throwable t) {
                            LOGGER.warn("Failed to process leftover pending update {}: {}", name, t.getMessage());
                        }
                    }
                });
            }
        } catch (Throwable t) {
            LOGGER.warn("Error running startup cleanup for pending updates: {}", t.getMessage());
        }
    }

    /**
     * Downloads the given release jar asynchronously and stages it for replacement on exit.
     */
    public static void startUpdateAsync(
        UpdateChecker.ReleaseInfo release,
        Consumer<String> onStatus,
        Consumer<String> onSuccess,
        Consumer<String> onError
    ) {
        if (release == null || !release.hasDownloadableJar()) {
            if (onError != null) onError.accept("Kein Download-Asset für dieses Release vorhanden.");
            return;
        }

        if (!DOWNLOADING.compareAndSet(false, true)) {
            if (onStatus != null) onStatus.accept("Ein Update-Download läuft bereits...");
            return;
        }

        Thread worker = new Thread(() -> {
            Path pendingPath = null;
            try {
                Path mods = getModsDirectory();
                Path currentJar = getCurrentJarPath();

                String jarName = release.jarName();
                if (jarName == null || jarName.isBlank()) {
                    jarName = "enhancedGM-" + release.version() + ".jar";
                }
                if (!jarName.endsWith(".jar")) {
                    jarName += ".jar";
                }

                Path targetJar = mods.resolve(jarName).toAbsolutePath().normalize();
                pendingPath = mods.resolve(jarName + ".pending").toAbsolutePath().normalize();

                LOGGER.info("Starting auto-update to {} from {}", jarName, release.jarUrl());

                downloadWithRedirects(release.jarUrl(), pendingPath);

                if (!Files.exists(pendingPath) || Files.size(pendingPath) < 50_000) {
                    throw new IllegalStateException("Heruntergeladene Datei ist unvollständig oder beschädigt.");
                }

                if (!isValidZip(pendingPath)) {
                    throw new IllegalStateException("Heruntergeladene Datei ist kein gültiges Jar-Archiv.");
                }

                // Verify SHA-256 Checksum
                String actualSha256 = computeSha256(pendingPath);
                LOGGER.info("Update-Datei {} heruntergeladen. Berechnete SHA-256: {}", jarName, actualSha256);

                String expectedSha256 = release.expectedSha256();
                if ((expectedSha256 == null || expectedSha256.isBlank()) && release.sha256AssetUrl() != null && !release.sha256AssetUrl().isBlank()) {
                    expectedSha256 = fetchSha256Text(release.sha256AssetUrl());
                }

                if (expectedSha256 != null && !expectedSha256.isBlank()) {
                    expectedSha256 = expectedSha256.trim().toLowerCase(Locale.ROOT);
                    if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
                        try { Files.deleteIfExists(pendingPath); } catch (Throwable ignored) {}
                        throw new SecurityException("SHA-256 Prüfsummenfehler! Erwartet: " + expectedSha256 + ", erhalten: " + actualSha256);
                    }
                    LOGGER.info("SHA-256 Prüfsumme erfolgreich verifiziert für {}", jarName);
                } else {
                    LOGGER.info("Keine externe Prüfsumme hinterlegt; Archivintegrität und TLS wurden erfolgreich verifiziert.");
                }

                boolean swapped = false;
                if (currentJar != null && currentJar.equals(targetJar)) {
                    // Same jar name: try atomic replacement
                    try {
                        Files.move(pendingPath, targetJar, StandardCopyOption.REPLACE_EXISTING);
                        swapped = true;
                    } catch (Throwable ignored) {
                        // Windows locked file, will swap on exit
                    }
                } else if (currentJar != null && !currentJar.equals(targetJar)) {
                    // Different jar name: promote pending file to targetJar
                    try {
                        Files.move(pendingPath, targetJar, StandardCopyOption.REPLACE_EXISTING);
                        // Try removing old jar now
                        try {
                            Files.deleteIfExists(currentJar);
                        } catch (Throwable ignored) {
                            // Old jar locked, schedule its deletion on exit
                        }
                        swapped = true;
                    } catch (Throwable ignored) {
                        // Pending file or directory issue, fallback to shutdown swap
                    }
                }

                if (!swapped) {
                    scheduleSwapOnExit(currentJar, pendingPath, targetJar);
                } else {
                    // If targetJar was created, ensure old jar is removed on exit if it still exists
                    if (currentJar != null && !currentJar.equals(targetJar) && Files.exists(currentJar)) {
                        scheduleOldJarDeletionOnExit(currentJar);
                    }
                }

                updateScheduledOnExit = true;
                if (onSuccess != null) {
                    onSuccess.accept(release.version());
                }
            } catch (Throwable t) {
                LOGGER.error("Auto-update failed: ", t);
                if (pendingPath != null) {
                    try { Files.deleteIfExists(pendingPath); } catch (Throwable ignored) {}
                }
                if (onError != null) {
                    onError.accept(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
                }
            } finally {
                DOWNLOADING.set(false);
            }
        }, "enhancedGM-AutoUpdateWorker");

        worker.setDaemon(true);
        worker.start();
    }

    private static void validateDownloadUrl(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            throw new SecurityException("Unsichere Update-URL (nur HTTPS erlaubt): " + uri);
        }
        String host = uri.getHost();
        if (host == null) {
            throw new SecurityException("Ungültige Update-URL (kein Host): " + uri);
        }
        host = host.toLowerCase(Locale.ROOT);
        boolean validHost = host.equals("github.com")
            || host.endsWith(".github.com")
            || host.equals("githubusercontent.com")
            || host.endsWith(".githubusercontent.com");
        if (!validHost) {
            throw new SecurityException("Nicht vertrauenswürdiger Download-Host: " + host);
        }
    }

    public static String computeSha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buf = new byte[16384];
            int r;
            while ((r = is.read(buf)) != -1) {
                md.update(buf, 0, r);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(64);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String fetchSha256Text(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            URI uri = URI.create(url);
            validateDownloadUrl(uri);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "enhancedGM-AutoUpdater");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() == 200) {
                try (var reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher m = Pattern.compile("(?i)\\b([a-f0-9]{64})\\b").matcher(line);
                        if (m.find()) {
                            return m.group(1).toLowerCase(Locale.ROOT);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Konnte SHA-256 Prüfsummen-Asset nicht laden: {}", t.getMessage());
        }
        return "";
    }

    private static void downloadWithRedirects(String initialUrl, Path destination) throws Exception {
        String currentUrl = initialUrl;
        int redirects = 0;

        while (redirects < 6) {
            URI uri = URI.create(currentUrl);
            validateDownloadUrl(uri);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "enhancedGM-AutoUpdater");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307
                || status == 308) {
                String location = conn.getHeaderField("Location");
                if (location == null || location.isBlank()) {
                    throw new IllegalStateException("HTTP redirect ohne Location-Header erhalten.");
                }
                currentUrl = location;
                redirects++;
                conn.disconnect();
                continue;
            }

            if (status != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP Fehler beim Download: " + status + " " + conn.getResponseMessage());
            }

            Files.createDirectories(destination.getParent());
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 OutputStream out = Files.newOutputStream(destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            conn.disconnect();
            return;
        }
        throw new IllegalStateException("Zu viele Weiterleitungen (Redirect Loop).");
    }

    private static boolean isValidZip(Path path) {
        try (ZipFile zf = new ZipFile(path.toFile())) {
            return zf.size() > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void scheduleSwapOnExit(Path currentJar, Path pendingPath, Path targetPath) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
                String curStr = currentJar != null ? currentJar.toAbsolutePath().toString() : "";
                String pendStr = pendingPath.toAbsolutePath().toString();
                String tgtStr = targetPath.toAbsolutePath().toString();

                if (isWindows) {
                    // Detached process waits for JVM to exit, deletes old jar, and moves pending file into place
                    String cmd = curStr.isEmpty()
                        ? String.format("ping 127.0.0.1 -n 3 > nul & move /y \"%s\" \"%s\"", pendStr, tgtStr)
                        : String.format("ping 127.0.0.1 -n 3 > nul & del /f /q \"%s\" & move /y \"%s\" \"%s\"", curStr, pendStr, tgtStr);

                    new ProcessBuilder("cmd.exe", "/c", cmd).start();
                } else {
                    String cmd = curStr.isEmpty()
                        ? String.format("sleep 2; mv '%s' '%s'", pendStr, tgtStr)
                        : String.format("sleep 2; rm -f '%s'; mv '%s' '%s'", curStr, pendStr, tgtStr);

                    new ProcessBuilder("sh", "-c", cmd).start();
                }
            } catch (Throwable t) {
                // Ignore errors during JVM shutdown
            }
        }, "enhancedGM-AutoUpdateShutdownHook"));
    }

    private static void scheduleOldJarDeletionOnExit(Path oldJar) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
                String oldStr = oldJar.toAbsolutePath().toString();
                if (isWindows) {
                    new ProcessBuilder("cmd.exe", "/c", "ping 127.0.0.1 -n 3 > nul & del /f /q \"" + oldStr + "\"").start();
                } else {
                    new ProcessBuilder("sh", "-c", "sleep 2; rm -f '" + oldStr + "'").start();
                }
            } catch (Throwable ignored) {}
        }, "enhancedGM-OldJarDeleteHook"));
    }
}
