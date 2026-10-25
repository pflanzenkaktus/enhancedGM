package de.pflanzenkaktus.enhancedgm.feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.pflanzenkaktus.enhancedgm.config.AddonConfig;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Lightweight asynchronous GitHub release update checker.
 * Only connects if consent was granted or when manually triggered in settings.
 */
public class UpdateChecker {
    public record ReleaseInfo(String version, String url, String title, String body) {}

    private final AddonConfig config;
    private final String currentVersion;
    private ReleaseInfo latestRelease = null;
    private boolean updateAvailable = false;
    private Consumer<ReleaseInfo> onUpdateFoundListener = null;
    private Runnable onUpToDateListener = null;
    private Consumer<String> onErrorListener = null;

    public UpdateChecker(AddonConfig config, String currentVersion) {
        this.config = config;
        this.currentVersion = currentVersion;
    }

    public void setOnUpdateFoundListener(Consumer<ReleaseInfo> listener) {
        this.onUpdateFoundListener = listener;
    }

    public void setOnUpToDateListener(Runnable listener) {
        this.onUpToDateListener = listener;
    }

    public void setOnErrorListener(Consumer<String> listener) {
        this.onErrorListener = listener;
    }

    public boolean hasUpdate() {
        return updateAvailable && latestRelease != null;
    }

    public ReleaseInfo getLatestRelease() {
        return latestRelease;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void checkForUpdatesAsync(boolean manualTrigger) {
        if (!manualTrigger && !config.checkUpdates) {
            return;
        }

        long now = System.currentTimeMillis();
        // Rate-limit automated checks to once every 4 hours
        if (!manualTrigger && (now - config.lastUpdateCheckEpochMs < 4L * 3600L * 1000L)) {
            return;
        }

        Thread checkThread = new Thread(() -> {
            try {
                URI uri = URI.create("https://api.github.com/repos/pflanzenkaktus/enhancedGM/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "enhancedGM-Addon");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    try (Reader r = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                        String tagName = json.has("tag_name") && !json.get("tag_name").isJsonNull()
                            ? json.get("tag_name").getAsString() : "";
                        String htmlUrl = json.has("html_url") && !json.get("html_url").isJsonNull()
                            ? json.get("html_url").getAsString() : "https://github.com/pflanzenkaktus/enhancedGM/releases/latest";
                        String title = json.has("name") && !json.get("name").isJsonNull()
                            ? json.get("name").getAsString() : tagName;
                        String body = json.has("body") && !json.get("body").isJsonNull()
                            ? json.get("body").getAsString() : "";

                        config.lastUpdateCheckEpochMs = System.currentTimeMillis();
                        config.latestReleaseUrl = htmlUrl;

                        if (isNewer(tagName, currentVersion)) {
                            this.latestRelease = new ReleaseInfo(tagName, htmlUrl, title, body);
                            this.updateAvailable = true;
                            if (onUpdateFoundListener != null) {
                                onUpdateFoundListener.accept(this.latestRelease);
                            }
                        } else {
                            this.updateAvailable = false;
                            if (manualTrigger && onUpToDateListener != null) {
                                onUpToDateListener.run();
                            }
                        }
                    }
                } else if (code == 404) {
                    // No release published yet
                    this.updateAvailable = false;
                    if (manualTrigger && onUpToDateListener != null) {
                        onUpToDateListener.run();
                    }
                }
            } catch (Exception e) {
                if (manualTrigger && onErrorListener != null) {
                    onErrorListener.accept(e.getMessage());
                }
            }
        }, "enhancedGM-UpdateChecker");
        checkThread.setDaemon(true);
        checkThread.start();
    }

    public static boolean isNewer(String remote, String current) {
        if (remote == null || current == null) return false;
        String cleanRemote = remote.trim();
        if (cleanRemote.startsWith("v") || cleanRemote.startsWith("V")) {
            cleanRemote = cleanRemote.substring(1);
        }
        String cleanCurrent = current.trim();
        if (cleanCurrent.startsWith("v") || cleanCurrent.startsWith("V")) {
            cleanCurrent = cleanCurrent.substring(1);
        }

        String[] remoteParts = cleanRemote.split("[.-]");
        String[] currentParts = cleanCurrent.split("[.-]");
        int len = Math.max(remoteParts.length, currentParts.length);
        for (int i = 0; i < len; i++) {
            int r = i < remoteParts.length ? parseSafeInt(remoteParts[i]) : 0;
            int c = i < currentParts.length ? parseSafeInt(currentParts[i]) : 0;
            if (r > c) return true;
            if (r < c) return false;
        }
        return false;
    }

    private static int parseSafeInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
