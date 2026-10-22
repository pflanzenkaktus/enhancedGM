package de.pflanzenkaktus.enhancedgm.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import de.pflanzenkaktus.enhancedgm.config.AddonConfig;
import de.pflanzenkaktus.enhancedgm.model.SpeedCamera;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Firebase-first speed camera repository that automatically updates and falls back to
 * a local JSON file (config/gm-addon/speed_cameras.json).
 *
 * <p>Features:
 * <ul>
 *   <li>Spatial deduplication so speed cameras never stack at the same location.</li>
 *   <li>Support for auto-detected speed cameras ({@code autoAdded = true}) when flashed in-game.</li>
 *   <li>Automatic background upload ({@code PATCH}) of newly discovered speed cameras to Firebase.</li>
 * </ul>
 */
public class SpeedCameraRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type CAMERA_LIST_TYPE = new TypeToken<List<SpeedCamera>>() {}.getType();

    public enum AddCameraResult {
        ADDED_NEW,
        MERGED_EXISTING
    }

    private final Path localDbPath;
    private final AddonConfig config;
    private final HttpClient httpClient;
    private final CopyOnWriteArrayList<SpeedCamera> cameras = new CopyOnWriteArrayList<>();

    private volatile long lastLocalModifiedMillis = -1L;
    private volatile long lastFirebaseSyncMillis = 0L;
    private volatile String syncStatus = "Local Fallback";
    private volatile String lastETag = null;

    public SpeedCameraRepository(Path localDbPath, AddonConfig config) {
        this.localDbPath = localDbPath;
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public void initialize() {
        ensureDefaultLocalDatabase();
        loadFromLocalDatabase();
        if (config.enableFirebaseCloudSync) {
            syncWithFirebaseAsync();
        } else {
            syncStatus = "Lokal (Cloud-Sync deaktiviert: " + cameras.size() + " Blitzer)";
        }
    }

    public List<SpeedCamera> getCameras() {
        return Collections.unmodifiableList(cameras);
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getLastETag() {
        return lastETag;
    }

    public void setLastETag(String lastETag) {
        this.lastETag = lastETag;
    }

    public Path getLocalDbPath() {
        return localDbPath;
    }

    /**
     * Checks whether an existing speed camera is already within {@code blitzerDedupeRadiusBlocks}
     * of the given coordinates.
     */
    public Optional<SpeedCamera> findNearbyCamera(double x, double y, double z) {
        double radius = Math.max(5.0, config.blitzerDedupeRadiusBlocks);
        for (SpeedCamera existing : cameras) {
            if (existing.horizontalDistanceTo(x, z) <= radius && Math.abs(existing.y() - y) <= 25.0) {
                return Optional.of(existing);
            }
        }
        return Optional.empty();
    }

    /**
     * Automatically records a speed camera when the player gets flashed ("geblitzt") in chat,
     * ensuring cameras never stack at the same location.
     */
    public synchronized AddCameraResult recordAutoDetectedCamera(
        double x,
        double y,
        double z,
         int speedLimitKmh,
        Float playerYaw,
        String addedByPlayer
    ) {
        int rx = (int) Math.round(x);
        int ry = (int) Math.round(y);
        int rz = (int) Math.round(z);
        int effectiveLimit = speedLimitKmh > 0 ? speedLimitKmh : 70;
        String id = String.format(Locale.ROOT, "auto_x%d_z%d", rx, rz);
        String name = String.format(Locale.ROOT, "Blitzer (%d km/h) [%d, %d]", effectiveLimit, rx, rz);

        SpeedCamera candidate = new SpeedCamera(
            id,
            name,
            Math.round(x * 10.0) / 10.0,
            Math.round(y * 10.0) / 10.0,
            Math.round(z * 10.0) / 10.0,
            effectiveLimit,
            playerYaw,
            0.0,
            true,
            (addedByPlayer != null && !addedByPlayer.isBlank()) ? addedByPlayer : "auto",
            System.currentTimeMillis()
        );
        return addOrMergeCamera(candidate);
    }

    /**
     * Adds a speed camera or merges it into an existing camera within {@code blitzerDedupeRadiusBlocks}
     * so multiple cameras never stack at the same location.
     */
    public synchronized AddCameraResult addOrMergeCamera(SpeedCamera candidate) {
        double radius = Math.max(5.0, config.blitzerDedupeRadiusBlocks);
        for (int i = 0; i < cameras.size(); i++) {
            SpeedCamera existing = cameras.get(i);
            if (existing.horizontalDistanceTo(candidate.x(), candidate.z()) <= radius
                && Math.abs(existing.y() - candidate.y()) <= 25.0) {
                // If existing camera had a different speed limit and was autoAdded, update its speed limit
                if (existing.autoAdded() && candidate.speedLimitKmh() > 0
                    && existing.speedLimitKmh() != candidate.speedLimitKmh()) {
                    SpeedCamera updated = new SpeedCamera(
                        existing.id(),
                        existing.name(),
                        existing.x(),
                        existing.y(),
                        existing.z(),
                        candidate.speedLimitKmh(),
                        existing.facingYaw() != null ? existing.facingYaw() : candidate.facingYaw(),
                        existing.triggerRadius(),
                        existing.autoAdded(),
                        existing.addedBy(),
                        existing.createdAtEpochMs()
                    );
                    cameras.set(i, updated);
                    saveToLocalDatabase(new ArrayList<>(cameras));
                    if (config.blitzerUploadAutoToFirebase && hasFirebaseUrl()) {
                        uploadCameraToFirebaseAsync(updated);
                    }
                }
                return AddCameraResult.MERGED_EXISTING;
            }
        }

        cameras.add(candidate);
        saveToLocalDatabase(new ArrayList<>(cameras));
        if (config.blitzerUploadAutoToFirebase && hasFirebaseUrl()) {
            uploadCameraToFirebaseAsync(candidate);
        }
        return AddCameraResult.ADDED_NEW;
    }

    /**
     * Deduplicates a list of cameras so that no two cameras are within {@code dedupeRadiusBlocks} of each other.
     * Manually curated cameras ({@code autoAdded == false}) take precedence over {@code autoAdded == true}.
     */
    public static List<SpeedCamera> deduplicateCameras(List<SpeedCamera> input, double dedupeRadiusBlocks) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        double radius = Math.max(5.0, dedupeRadiusBlocks);
        List<SpeedCamera> sorted = new ArrayList<>(input);
        // Prefer manual cameras (autoAdded = false) first so they become the canonical anchor
        sorted.sort((a, b) -> Boolean.compare(a.autoAdded(), b.autoAdded()));

        List<SpeedCamera> unique = new ArrayList<>();
        for (SpeedCamera candidate : sorted) {
            boolean duplicate = false;
            for (SpeedCamera kept : unique) {
                if (kept.horizontalDistanceTo(candidate.x(), candidate.z()) <= radius
                    && Math.abs(kept.y() - candidate.y()) <= 25.0) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                unique.add(candidate);
            }
        }
        return unique;
    }

    /**
     * Uploads a single speed camera entry (including its {@code autoAdded: true} flag)
     * to Firebase Realtime Database or Cloud Firestore.
     */
    public CompletableFuture<Boolean> uploadCameraToFirebaseAsync(SpeedCamera camera) {
        if (!hasFirebaseUrl() || camera == null) {
            return CompletableFuture.completedFuture(false);
        }

        String baseUrl = buildAuthenticatedFirebaseUrl(config.firebaseDatabaseUrl.trim());
        JsonObject camJson = toCameraJsonObject(camera);

        String payload;
        HttpRequest request;
        if (baseUrl.contains("firestore.googleapis.com")) {
            payload = toFirestoreDocumentJson(camera).toString();
            String docUrl = baseUrl.contains("?")
                ? baseUrl + "&documentId=" + camera.id()
                : baseUrl + "?documentId=" + camera.id();
            request = HttpRequest.newBuilder()
                .uri(URI.create(docUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        } else {
            // Firebase Realtime Database REST API: PATCH { "<id>": { ... } } to the collection .json endpoint
            JsonObject wrapper = new JsonObject();
            wrapper.add(camera.id(), camJson);
            payload = GSON.toJson(wrapper);
            request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=UTF-8")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(res -> {
                boolean ok = res.statusCode() >= 200 && res.statusCode() < 300;
                if (ok) {
                    this.lastETag = null;
                    syncStatus = "Firebase Updated (" + cameras.size() + " Blitzer)";
                }
                return ok;
            })
            .exceptionally(ex -> false);
    }

    /**
     * Checks whether the local fallback file was edited manually on disk, and triggers
     * periodic Firebase synchronization if configured.
     */
    public void tickCheckForUpdates() {
        try {
            if (Files.exists(localDbPath)) {
                long modified = Files.getLastModifiedTime(localDbPath).toMillis();
                if (lastLocalModifiedMillis != -1L && modified > lastLocalModifiedMillis) {
                    loadFromLocalDatabase();
                }
            }
        } catch (IOException ignored) {
        }

        long now = System.currentTimeMillis();
        long intervalMs = Math.max(30, config.firebaseSyncIntervalSeconds) * 1000L;
        if (hasFirebaseUrl() && (now - lastFirebaseSyncMillis >= intervalMs)) {
            syncWithFirebaseAsync();
        }
    }

    public boolean hasFirebaseUrl() {
        return config.enableFirebaseCloudSync
            && config.firebaseDatabaseUrl != null
            && !config.firebaseDatabaseUrl.isBlank();
    }

    private String buildAuthenticatedFirebaseUrl(String rawUrl) {
        String url = rawUrl;
        if (!url.contains("firestore.googleapis.com") && !url.endsWith(".json") && !url.contains(".json?")) {
            if (url.endsWith("/")) {
                url = url + "speed_cameras.json";
            } else {
                url = url + ".json";
            }
        }
        if (config.firebaseApiKey != null && !config.firebaseApiKey.isBlank()) {
            String separator = url.contains("?") ? "&" : "?";
            if (!url.contains("key=") && !url.contains("auth=")) {
                String paramName = url.contains("firestore.googleapis.com") ? "key=" : "auth=";
                url = url + separator + paramName + config.firebaseApiKey.trim();
            }
        }
        return url;
    }

    public CompletableFuture<Boolean> syncWithFirebaseAsync() {
        if (!config.enableFirebaseCloudSync) {
            syncStatus = "Lokal (Cloud-Sync deaktiviert: " + cameras.size() + " Blitzer)";
            return CompletableFuture.completedFuture(false);
        }
        lastFirebaseSyncMillis = System.currentTimeMillis();
        if (!hasFirebaseUrl()) {
            syncStatus = "Keine Firebase-URL (" + cameras.size() + " Blitzer lokal)";
            return CompletableFuture.completedFuture(false);
        }

        String url = buildAuthenticatedFirebaseUrl(config.firebaseDatabaseUrl.trim());

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .GET();

        if (lastETag != null && !lastETag.isBlank()) {
            reqBuilder.header("If-None-Match", lastETag);
        }

        HttpRequest request = reqBuilder.build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(response -> {
                int status = response.statusCode();
                if (status == 304) {
                    syncStatus = "Firebase Synced (Cached: " + cameras.size() + " Blitzer)";
                    return true;
                }
                if (status >= 200 && status < 300) {
                    this.lastETag = response.headers().firstValue("ETag").orElse(null);
                    List<SpeedCamera> fetched = parseFirebaseJson(response.body());

                    // Protect against empty/corrupted response wiping existing local cameras
                    if (fetched.isEmpty() && !cameras.isEmpty() && !"[]".equals(response.body().trim())) {
                        syncStatus = "Firebase Empty Response -> Local Preserved (" + cameras.size() + " Blitzer)";
                        return false;
                    }

                    List<SpeedCamera> deduped = deduplicateCameras(fetched, config.blitzerDedupeRadiusBlocks);

                    // Preserve newly added in-flight local cameras to prevent race-condition overwrite
                    List<SpeedCamera> merged = new ArrayList<>(deduped);
                    for (SpeedCamera localCam : cameras) {
                        boolean existsInRemote = merged.stream().anyMatch(c -> c.id().equals(localCam.id())
                            || Math.hypot(c.x() - localCam.x(), c.z() - localCam.z()) <= config.blitzerDedupeRadiusBlocks);
                        if (!existsInRemote) {
                            merged.add(localCam);
                        }
                    }

                    List<SpeedCamera> finalCameras = deduplicateCameras(merged, config.blitzerDedupeRadiusBlocks);
                    cameras.clear();
                    cameras.addAll(finalCameras);
                    saveToLocalDatabase(finalCameras);
                    syncStatus = "Firebase Synced (" + finalCameras.size() + " Blitzer)";
                    return true;
                }
                syncStatus = "Firebase Fallback -> Local (" + cameras.size() + " Blitzer)";
                return false;
            })
            .exceptionally(ex -> {
                syncStatus = "Offline -> Local Fallback (" + cameras.size() + " Blitzer)";
                return false;
            });
    }

    /**
     * Parses both Firebase Realtime Database JSON (array or keyed object map)
     * and Cloud Firestore REST API JSON (documents array with fields).
     */
    public static List<SpeedCamera> parseFirebaseJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank() || jsonText.trim().equals("null")) {
            return List.of();
        }
        JsonElement root = JsonParser.parseString(jsonText);
        List<SpeedCamera> result = new ArrayList<>();

        if (root.isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray()) {
                if (el.isJsonObject()) {
                    SpeedCamera cam = parseCameraObject(null, el.getAsJsonObject());
                    if (cam != null) {
                        result.add(cam);
                    }
                }
            }
        } else if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            if (obj.has("documents") && obj.get("documents").isJsonArray()) {
                JsonArray docs = obj.getAsJsonArray("documents");
                for (JsonElement docEl : docs) {
                    if (docEl.isJsonObject()) {
                        SpeedCamera cam = parseFirestoreDocument(docEl.getAsJsonObject());
                        if (cam != null) {
                            result.add(cam);
                        }
                    }
                }
            } else if (obj.has("cameras") && obj.get("cameras").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("cameras")) {
                    if (el.isJsonObject()) {
                        SpeedCamera cam = parseCameraObject(null, el.getAsJsonObject());
                        if (cam != null) {
                            result.add(cam);
                        }
                    }
                }
            } else {
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    if (entry.getValue().isJsonObject()) {
                        SpeedCamera cam = parseCameraObject(entry.getKey(), entry.getValue().getAsJsonObject());
                        if (cam != null) {
                            result.add(cam);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static SpeedCamera parseCameraObject(String fallbackId, JsonObject obj) {
        try {
            String id = obj.has("id") && !obj.get("id").isJsonNull()
                ? obj.get("id").getAsString()
                : (fallbackId != null ? fallbackId : "blitzer_" + Math.abs(obj.hashCode()));
            String name = sanitizeCameraName(obj.has("name") && !obj.get("name").isJsonNull()
                ? obj.get("name").getAsString()
                : "Blitzer");
            double x = obj.get("x").getAsDouble();
            double y = obj.has("y") && !obj.get("y").isJsonNull() ? obj.get("y").getAsDouble() : 64.0;
            double z = obj.get("z").getAsDouble();
            int speedLimit = obj.has("speedLimitKmh") && !obj.get("speedLimitKmh").isJsonNull()
                ? obj.get("speedLimitKmh").getAsInt()
                : 70;
            Float facingYaw = obj.has("facingYaw") && !obj.get("facingYaw").isJsonNull()
                ? obj.get("facingYaw").getAsFloat()
                : null;
            double triggerRadius = obj.has("triggerRadius") && !obj.get("triggerRadius").isJsonNull()
                ? obj.get("triggerRadius").getAsDouble()
                : 0.0;
            boolean autoAdded = obj.has("autoAdded") && !obj.get("autoAdded").isJsonNull()
                && obj.get("autoAdded").getAsBoolean();
            String addedBy = sanitizeAddedBy(obj.has("addedBy") && !obj.get("addedBy").isJsonNull()
                ? obj.get("addedBy").getAsString()
                : (autoAdded ? "auto" : "manual"));
            long createdAt = obj.has("createdAtEpochMs") && !obj.get("createdAtEpochMs").isJsonNull()
                ? obj.get("createdAtEpochMs").getAsLong()
                : 0L;
            return new SpeedCamera(id, name, x, y, z, speedLimit, facingYaw, triggerRadius, autoAdded, addedBy, createdAt);
        } catch (Exception e) {
            return null;
        }
    }

    private static SpeedCamera parseFirestoreDocument(JsonObject doc) {
        try {
            String docName = doc.has("name") ? doc.get("name").getAsString() : "blitzer";
            String id = docName.contains("/") ? docName.substring(docName.lastIndexOf('/') + 1) : docName;
            if (!doc.has("fields") || !doc.get("fields").isJsonObject()) {
                return null;
            }
            JsonObject fields = doc.getAsJsonObject("fields");
            String name = sanitizeCameraName(getFirestoreString(fields, "name", "Blitzer"));
            double x = getFirestoreDouble(fields, "x", 0.0);
            double y = getFirestoreDouble(fields, "y", 64.0);
            double z = getFirestoreDouble(fields, "z", 0.0);
            int speedLimit = (int) getFirestoreDouble(fields, "speedLimitKmh", 70.0);
            Float facingYaw = fields.has("facingYaw")
                ? (float) getFirestoreDouble(fields, "facingYaw", 0.0)
                : null;
            double triggerRadius = getFirestoreDouble(fields, "triggerRadius", 0.0);
            boolean autoAdded = getFirestoreBoolean(fields, "autoAdded", false);
            String addedBy = sanitizeAddedBy(getFirestoreString(fields, "addedBy", autoAdded ? "auto" : "manual"));
            long createdAt = (long) getFirestoreDouble(fields, "createdAtEpochMs", 0.0);
            return new SpeedCamera(id, name, x, y, z, speedLimit, facingYaw, triggerRadius, autoAdded, addedBy, createdAt);
        } catch (Exception e) {
            return null;
        }
    }

    public static String sanitizeCameraName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Blitzer";
        }
        String cleaned = raw.replaceAll("(?i)https?://\\S+", "")
            .replaceAll("(?i)www\\.\\S+", "")
            .replaceAll("(?i)discord\\.gg/\\S+", "")
            .replaceAll("[^A-Za-z0-9ÄÖÜäöüß (),._-]", "")
            .trim();
        if (cleaned.length() > 55) {
            cleaned = cleaned.substring(0, 55).trim();
        }
        return cleaned.length() >= 2 ? cleaned : "Blitzer";
    }

    public static String sanitizeAddedBy(String raw) {
        if (raw == null || raw.isBlank()) {
            return "auto";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9_-]", "").trim();
        if (cleaned.length() > 24) {
            cleaned = cleaned.substring(0, 24);
        }
        return cleaned.length() >= 2 ? cleaned : "auto";
    }

    private static JsonObject toCameraJsonObject(SpeedCamera cam) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", cam.id());
        obj.addProperty("name", sanitizeCameraName(cam.name()));
        obj.addProperty("x", cam.x());
        obj.addProperty("y", cam.y());
        obj.addProperty("z", cam.z());
        obj.addProperty("speedLimitKmh", cam.speedLimitKmh());
        if (cam.facingYaw() != null) {
            obj.addProperty("facingYaw", cam.facingYaw());
        }
        obj.addProperty("triggerRadius", cam.triggerRadius());
        obj.addProperty("autoAdded", cam.autoAdded());
        obj.addProperty("addedBy", sanitizeAddedBy(cam.addedBy()));
        obj.addProperty("createdAtEpochMs", cam.createdAtEpochMs());
        return obj;
    }

    private static JsonObject toFirestoreDocumentJson(SpeedCamera cam) {
        JsonObject fields = new JsonObject();
        fields.add("name", firestoreString(cam.name()));
        fields.add("x", firestoreDouble(cam.x()));
        fields.add("y", firestoreDouble(cam.y()));
        fields.add("z", firestoreDouble(cam.z()));
        fields.add("speedLimitKmh", firestoreInt(cam.speedLimitKmh()));
        fields.add("triggerRadius", firestoreDouble(cam.triggerRadius()));
        fields.add("autoAdded", firestoreBool(cam.autoAdded()));
        fields.add("addedBy", firestoreString(cam.addedBy() != null ? cam.addedBy() : "auto"));
        fields.add("createdAtEpochMs", firestoreInt(cam.createdAtEpochMs()));

        JsonObject doc = new JsonObject();
        doc.add("fields", fields);
        return doc;
    }

    private static JsonObject firestoreString(String v) {
        JsonObject o = new JsonObject();
        o.addProperty("stringValue", v);
        return o;
    }

    private static JsonObject firestoreDouble(double v) {
        JsonObject o = new JsonObject();
        o.addProperty("doubleValue", v);
        return o;
    }

    private static JsonObject firestoreInt(long v) {
        JsonObject o = new JsonObject();
        o.addProperty("integerValue", Long.toString(v));
        return o;
    }

    private static JsonObject firestoreBool(boolean v) {
        JsonObject o = new JsonObject();
        o.addProperty("booleanValue", v);
        return o;
    }

    private static String getFirestoreString(JsonObject fields, String key, String defaultVal) {
        if (!fields.has(key) || !fields.get(key).isJsonObject()) return defaultVal;
        JsonObject field = fields.getAsJsonObject(key);
        if (field.has("stringValue")) return field.get("stringValue").getAsString();
        return defaultVal;
    }

    private static double getFirestoreDouble(JsonObject fields, String key, double defaultVal) {
        if (!fields.has(key) || !fields.get(key).isJsonObject()) return defaultVal;
        JsonObject field = fields.getAsJsonObject(key);
        if (field.has("doubleValue")) return field.get("doubleValue").getAsDouble();
        if (field.has("integerValue")) return Double.parseDouble(field.get("integerValue").getAsString());
        return defaultVal;
    }

    private static boolean getFirestoreBoolean(JsonObject fields, String key, boolean defaultVal) {
        if (!fields.has(key) || !fields.get(key).isJsonObject()) return defaultVal;
        JsonObject field = fields.getAsJsonObject(key);
        if (field.has("booleanValue")) return field.get("booleanValue").getAsBoolean();
        return defaultVal;
    }

    public synchronized void loadFromLocalDatabase() {
        if (!Files.exists(localDbPath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(localDbPath, StandardCharsets.UTF_8)) {
            String raw = Files.readString(localDbPath, StandardCharsets.UTF_8);
            List<SpeedCamera> loaded = parseFirebaseJson(raw);
            if (loaded.isEmpty()) {
                List<SpeedCamera> direct = GSON.fromJson(reader, CAMERA_LIST_TYPE);
                if (direct != null) {
                    loaded = direct;
                }
            }
            List<SpeedCamera> deduped = deduplicateCameras(loaded, config.blitzerDedupeRadiusBlocks);
            cameras.clear();
            cameras.addAll(deduped);
            lastLocalModifiedMillis = Files.getLastModifiedTime(localDbPath).toMillis();
            if (!hasFirebaseUrl()) {
                syncStatus = "Local Fallback (" + cameras.size() + " Blitzer)";
            }
        } catch (Exception e) {
            System.err.println("[GM-Addon] Error reading speed_cameras.json: " + e.getMessage());
        }
    }

    public synchronized void saveToLocalDatabase(List<SpeedCamera> listToSave) {
        try {
            if (localDbPath.getParent() != null) {
                Files.createDirectories(localDbPath.getParent());
            }
            List<SpeedCamera> deduped = deduplicateCameras(listToSave, config.blitzerDedupeRadiusBlocks);
            Path tempFile = localDbPath.resolveSibling(localDbPath.getFileName().toString() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                GSON.toJson(deduped, writer);
            }
            Files.move(tempFile, localDbPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            lastLocalModifiedMillis = Files.getLastModifiedTime(localDbPath).toMillis();
        } catch (IOException e) {
            System.err.println("[GM-Addon] Error saving local fallback speed_cameras.json: " + e.getMessage());
        }
    }

    private void ensureDefaultLocalDatabase() {
        if (Files.exists(localDbPath)) {
            return;
        }
        List<SpeedCamera> seed = List.of(
            new SpeedCamera("hbf_nord", "Hauptbahnhof Zufahrt", -885.0, 63.0, -2005.0, 70, null, 0.0)
        );
        saveToLocalDatabase(seed);
    }
}
