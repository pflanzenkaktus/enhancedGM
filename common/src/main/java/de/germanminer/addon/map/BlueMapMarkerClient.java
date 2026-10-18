package de.germanminer.addon.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.germanminer.addon.config.AddonConfig;
import de.germanminer.addon.model.BlueMapMarker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Periodically fetches and parses GermanMiner's live BlueMap markers
 * (http://map.germanminer.de:2086/maps/world/live/markers.json).
 */
public class BlueMapMarkerClient {
    private final AddonConfig config;
    private final HttpClient httpClient;
    private final CopyOnWriteArrayList<BlueMapMarker> markers = new CopyOnWriteArrayList<>();
    private volatile long lastRefreshMillis = 0L;

    public BlueMapMarkerClient(AddonConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public List<BlueMapMarker> getMarkers() {
        return Collections.unmodifiableList(markers);
    }

    public void tickCheckForRefresh() {
        long now = System.currentTimeMillis();
        long intervalMs = Math.max(60, config.markerRefreshIntervalSeconds) * 1000L;
        if (now - lastRefreshMillis >= intervalMs) {
            refreshAsync();
        }
    }

    public CompletableFuture<Integer> refreshAsync() {
        lastRefreshMillis = System.currentTimeMillis();
        String base = config.blueMapBaseUrl.endsWith("/")
            ? config.blueMapBaseUrl.substring(0, config.blueMapBaseUrl.length() - 1)
            : config.blueMapBaseUrl;
        String url = String.format("%s/maps/%s/live/markers.json", base, config.blueMapWorldId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(12))
            .GET()
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(resp -> {
                if (resp.statusCode() == 200 && resp.body() != null) {
                    List<BlueMapMarker> parsed = parseMarkersJson(resp.body());
                    markers.clear();
                    markers.addAll(parsed);
                    return parsed.size();
                }
                return 0;
            })
            .exceptionally(ex -> 0);
    }

    public static List<BlueMapMarker> parseMarkersJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return List.of();
        }
        List<BlueMapMarker> result = new ArrayList<>();
        JsonElement rootEl = JsonParser.parseString(jsonText);
        if (!rootEl.isJsonObject()) {
            return result;
        }
        JsonObject root = rootEl.getAsJsonObject();

        for (Map.Entry<String, JsonElement> setEntry : root.entrySet()) {
            String categoryId = setEntry.getKey();
            if (!setEntry.getValue().isJsonObject()) continue;
            JsonObject setObj = setEntry.getValue().getAsJsonObject();

            String categoryLabel = setObj.has("label") && !setObj.get("label").isJsonNull()
                ? setObj.get("label").getAsString()
                : categoryId;
            int color = colorForCategory(categoryId);

            if (!setObj.has("markers") || !setObj.get("markers").isJsonObject()) continue;
            JsonObject markersObj = setObj.getAsJsonObject("markers");

            for (Map.Entry<String, JsonElement> mEntry : markersObj.entrySet()) {
                if (!mEntry.getValue().isJsonObject()) continue;
                JsonObject m = mEntry.getValue().getAsJsonObject();
                String type = m.has("type") ? m.get("type").getAsString() : "poi";
                // Focus on point-of-interest markers (Taxipunkte, Tankstellen, Haltestellen)
                if (!"poi".equalsIgnoreCase(type) && !"html".equalsIgnoreCase(type)) {
                    continue;
                }
                if (!m.has("position") || !m.get("position").isJsonObject()) continue;
                JsonObject pos = m.getAsJsonObject("position");
                double x = pos.has("x") ? pos.get("x").getAsDouble() : 0.0;
                double y = pos.has("y") ? pos.get("y").getAsDouble() : 64.0;
                double z = pos.has("z") ? pos.get("z").getAsDouble() : 0.0;

                String label = m.has("label") && !m.get("label").isJsonNull()
                    ? m.get("label").getAsString()
                    : categoryLabel;
                String detail = m.has("detail") && !m.get("detail").isJsonNull()
                    ? m.get("detail").getAsString()
                    : label;

                result.add(new BlueMapMarker(
                    mEntry.getKey(),
                    categoryId,
                    categoryLabel,
                    label,
                    detail,
                    x, y, z,
                    color
                ));
            }
        }
        return result;
    }

    public static int colorForCategory(String categoryId) {
        if (categoryId == null) return 0xFF38BDF8;
        return switch (categoryId) {
            case "Tankstellen" -> 0xFFF59E0B;           // Amber / Orange
            case "Taxipunkte" -> 0xFFFACC15;            // Taxi Yellow
            case "Bushaltestellen" -> 0xFF22C55E;       // Green
            case "S-Bahnhaltestellen" -> 0xFF3B82F6;    // Blue
            case "Kabelwagenhaltestellen" -> 0xFFA855F7;// Purple
            default -> 0xFF38BDF8;                      // Sky Blue
        };
    }
}
