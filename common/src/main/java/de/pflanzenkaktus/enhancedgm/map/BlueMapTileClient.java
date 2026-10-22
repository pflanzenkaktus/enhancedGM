package de.pflanzenkaktus.enhancedgm.map;

import de.pflanzenkaktus.enhancedgm.config.AddonConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Non-blocking asynchronous tile client for GermanMiner's BlueMap server
 * (http://map.germanminer.de:2086/maps/world/tiles/{lod}/x{tx}/z{tz}.png).
 *
 * Uses separate non-blocking HttpClient async I/O so requesting dozens of tiles simultaneously
 * on the Minimap or Fullscreen Map never deadlocks worker threads.
 */
public class BlueMapTileClient {
    public static final int TILE_PIXELS = 500;
    public static final int LOD1_BLOCKS_PER_TILE = 500;
    public static final int LOD_FACTOR = 5;

    public record TileKey(int lod, int tileX, int tileZ) {}

    private final Path cacheDir;
    private final AddonConfig config;
    private final HttpClient httpClient;
    private final ExecutorService diskIoPool;

    private static final int MAX_MEMORY_TILES = 128;
    private final Map<TileKey, byte[]> memoryPngCache = java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<TileKey, byte[]> eldest) {
                return size() > MAX_MEMORY_TILES;
            }
        }
    );
    private final Set<TileKey> inFlightRequests = ConcurrentHashMap.newKeySet();
    private final Map<TileKey, Long> emptyOrFailedUntilMs = new ConcurrentHashMap<>();

    public BlueMapTileClient(Path cacheDir, AddonConfig config) {
        this.cacheDir = cacheDir;
        this.config = config;
        this.diskIoPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "GM-Addon-BlueMap-DiskWorker");
            t.setDaemon(true);
            return t;
        });
        // IMPORTANT: Do NOT share a fixed-size blocking pool with HttpClient's internal selector!
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public static int blocksPerTile(int lod) {
        int scale = 1;
        for (int i = 1; i < lod; i++) {
            scale *= LOD_FACTOR;
        }
        return LOD1_BLOCKS_PER_TILE * scale;
    }

    public static int blockToTileCoord(double blockCoord, int lod) {
        int size = blocksPerTile(lod);
        return (int) Math.floor(blockCoord / (double) size);
    }

    public static double blockToTileUv(double blockCoord, int tileCoord, int lod) {
        int size = blocksPerTile(lod);
        double origin = (double) tileCoord * size;
        return (blockCoord - origin) / (double) size;
    }

    public String buildTileUrl(TileKey key) {
        String base = config.blueMapBaseUrl.endsWith("/")
            ? config.blueMapBaseUrl.substring(0, config.blueMapBaseUrl.length() - 1)
            : config.blueMapBaseUrl;
        String xPart = formatSignedCoordPath("x", key.tileX());
        String zPart = formatSignedCoordPath("z", key.tileZ());
        return String.format("%s/maps/%s/tiles/%d/%s/%s.png", base, config.blueMapWorldId, key.lod(), xPart, zPart);
    }

    public static String formatSignedCoordPath(String prefix, int coord) {
        StringBuilder sb = new StringBuilder(prefix);
        if (coord < 0) {
            sb.append('-');
            coord = -coord;
        }
        String digits = Integer.toString(coord);
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    /**
     * Returns the PNG bytes if already in memory, or starts an asynchronous disk/HTTP fetch.
     */
    public byte[] getOrRequestTilePng(TileKey key) {
        byte[] cached = memoryPngCache.get(key);
        if (cached != null) {
            return cached;
        }

        long now = System.currentTimeMillis();
        Long retryAfter = emptyOrFailedUntilMs.get(key);
        if (retryAfter != null && now < retryAfter) {
            return null;
        }
        emptyOrFailedUntilMs.remove(key);

        if (!inFlightRequests.add(key)) {
            return null;
        }

        Path diskFile = getDiskCachePath(key);
        long ttlMs = Math.max(1, config.tileCacheTtlMinutes) * 60_000L;

        CompletableFuture.runAsync(() -> {
            try {
                if (Files.exists(diskFile)) {
                    long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(diskFile).toMillis();
                    byte[] bytes = Files.readAllBytes(diskFile);
                    if (bytes.length > 100) {
                        memoryPngCache.put(key, bytes);
                        if (ageMs < ttlMs) {
                            inFlightRequests.remove(key);
                            return;
                        }
                    }
                }
                fetchFromRemoteAsync(key, diskFile);
            } catch (Exception e) {
                inFlightRequests.remove(key);
            }
        }, diskIoPool);

        return null;
    }

    private void fetchFromRemoteAsync(TileKey key, Path diskFile) {
        String url = buildTileUrl(key);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "GM-Addon/1.0")
            .GET()
            .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .whenCompleteAsync((response, error) -> {
                try {
                    if (error != null || response == null) {
                        // Retry after 8 seconds on transient network error
                        emptyOrFailedUntilMs.put(key, System.currentTimeMillis() + 8_000L);
                        return;
                    }
                    int status = response.statusCode();
                    if (status == 204 || status == 404) {
                        // Unrendered void tile on BlueMap; recheck after 5 minutes
                        emptyOrFailedUntilMs.put(key, System.currentTimeMillis() + 300_000L);
                        return;
                    }
                    byte[] body = response.body();
                    if (status == 200 && body != null && body.length > 100) {
                        memoryPngCache.put(key, body);
                        if (diskFile.getParent() != null) {
                            Files.createDirectories(diskFile.getParent());
                        }
                        Files.write(diskFile, body);
                    } else {
                        emptyOrFailedUntilMs.put(key, System.currentTimeMillis() + 10_000L);
                    }
                } catch (Exception ignored) {
                } finally {
                    inFlightRequests.remove(key);
                }
            }, diskIoPool);
    }

    /**
     * Clears both RAM and disk caches so clicking "Aktualisieren" forces a fresh download
     * from GermanMiner's BlueMap server.
     */
    public void clearCacheAndReload() {
        memoryPngCache.clear();
        emptyOrFailedUntilMs.clear();
        inFlightRequests.clear();
        CompletableFuture.runAsync(() -> {
            try {
                if (Files.exists(cacheDir)) {
                    try (var stream = Files.list(cacheDir)) {
                        stream.filter(p -> p.toString().endsWith(".png")).forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
                    }
                }
            } catch (IOException ignored) {
            }
        }, diskIoPool);
    }

    public int getLoadedTileCount() {
        return memoryPngCache.size();
    }

    public int getPendingRequestCount() {
        return inFlightRequests.size();
    }

    private Path getDiskCachePath(TileKey key) {
        return cacheDir.resolve(String.format("lod%d_x%d_z%d.png", key.lod(), key.tileX(), key.tileZ()));
    }
}
