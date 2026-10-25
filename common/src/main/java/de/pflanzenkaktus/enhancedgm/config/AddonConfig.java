package de.pflanzenkaktus.enhancedgm.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AddonConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Auto-update checker & privacy consent
    public boolean checkUpdates = true;
    public boolean updateConsentPromptDismissed = false;
    public String latestReleaseUrl = "https://github.com/pflanzenkaktus/enhancedGM/releases/latest";
    public long lastUpdateCheckEpochMs = 0L;

    // Server activation & language
    public boolean forceEnableEverywhere = false; // Default false for release safety; also auto-detects germanminer.de
    public String serverDomainPattern = "germanminer.de";
    public String language = "AUTO"; // "AUTO", "EN", "DE"

    // Minimap HUD settings
    public boolean minimapEnabled = true;
    public int minimapX = 12;
    public int minimapY = 12;
    public int minimapSize = 130;
    public float minimapScale = 1.0f;
    public boolean minimapRotateWithPlayer = true;
    public double minimapZoomBlocks = 160.0; // Visible diameter in blocks
    public boolean showSpeedCamerasOnMap = true;
    public boolean showBlueMapMarkersOnMap = true;
    public List<String> enabledMarkerSets = new ArrayList<>(Arrays.asList(
        "Tankstellen",
        "Taxipunkte",
        "Bushaltestellen",
        "S-Bahnhaltestellen",
        "Kabelwagenhaltestellen"
    ));

    // Speed Camera (Blitzer) Warning HUD & logic settings
    public boolean blitzerWidgetEnabled = true;
    public boolean blitzerOnlyInVehicle = true;
    public boolean blitzerAutoAddOnFlash = true;
    public boolean blitzerUploadAutoToFirebase = true;
    public double blitzerDedupeRadiusBlocks = 28.0;
    public int blitzerWidgetX = 160;
    public int blitzerWidgetY = 14;
    public float blitzerWidgetScale = 1.0f;
    public double warningDistanceBlocks = 110.0;
    public double urgentDistanceBlocks = 45.0;
    public double directionConeDegrees = 75.0;
    public double minSpeedBlocksPerSec = 1.5;
    public boolean playWarningSound = true;
    public boolean render3dWorldMarkers = true;

    // Community Cloud Sync (Firebase)
    public boolean enableFirebaseCloudSync = false;
    public boolean firebaseConsentPromptDismissed = false;
    public String firebaseDatabaseUrl = "https://gm-addon-a5507-default-rtdb.europe-west1.firebasedatabase.app/speed_cameras.json";
    public String firebaseApiKey = "";
    public int firebaseSyncIntervalSeconds = 300;

    // BlueMap endpoint & cache settings
    public String blueMapBaseUrl = "http://map.germanminer.de:2086";
    public String blueMapWorldId = "world";
    public int tileCacheTtlMinutes = 60;
    public int markerRefreshIntervalSeconds = 300;

    // Spawn / Respawn Protection (Spawnschutz) Widget settings
    public boolean spawnProtectionWidgetEnabled = true;
    public int spawnProtectionX = 12;
    public int spawnProtectionY = 172;
    public float spawnProtectionScale = 1.0f;
    public int spawnProtectionSeconds = 20;
    public boolean triggerSpawnProtectionOnJoin = true;
    public boolean triggerSpawnProtectionOnRespawn = true;

    // Plot expiry reminder (Grundstücke & Wohnungen, 60-day rule)
    public boolean plotReminderEnabled = true;
    public boolean plotWidgetEnabled = true;
    public String plotCompanyName = ""; // Owner name shown as "<Name> (Firma)" on company plots
    public int plotReminderDaysBefore = 7;
    public int plotReminderIntervalMinutes = 30; // 0 = only on join, >0 = periodic reminder in minutes
    public long plotLastScanEpochMs = 0L; // last completed scan (even if empty), 0 = never
    public int plotRescanPromptDays = 30;
    public int plotMatchRadiusBlocks = 150;
    public int plotWidgetX = 12;
    public int plotWidgetY = 212;
    public float plotWidgetScale = 1.0f;

    // Speedometer (Tacho) & Tempomat HUD settings
    public double speedCalibrationFactor = 2.70; // GermanMiner calibration: 50 km/h server = ~18.5 blocks/s (18.5 * 2.7 = 50 km/h)
    public boolean tachoEnabled = true;
    public boolean tachoOnlyInVehicle = true;
    public boolean autoMotorStart = true;
    public String tachoStyle = "ANALOG"; // "ANALOG" or "DIGITAL"
    public int tachoX = 200;
    public int tachoY = 180;
    public float tachoScale = 1.0f;
    public int tachoMaxKmh = 200;

    public static AddonConfig load(Path configFile) {
        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                AddonConfig loaded = GSON.fromJson(reader, AddonConfig.class);
                if (loaded != null) {
                    if (loaded.enabledMarkerSets == null) {
                        loaded.enabledMarkerSets = new ArrayList<>();
                    }
                    if (loaded.language == null || loaded.language.isBlank()) {
                        loaded.language = "AUTO";
                    }
                    if (loaded.firebaseDatabaseUrl == null || loaded.firebaseDatabaseUrl.isBlank()) {
                        loaded.firebaseDatabaseUrl = "https://gm-addon-a5507-default-rtdb.europe-west1.firebasedatabase.app/speed_cameras.json";
                    }
                    if (loaded.speedCalibrationFactor <= 0.1) {
                        loaded.speedCalibrationFactor = 2.70;
                    }
                    if (loaded.tachoStyle == null || loaded.tachoStyle.isBlank()) {
                        loaded.tachoStyle = "ANALOG";
                    }
                    if (loaded.tachoScale <= 0.1f) {
                        loaded.tachoScale = 1.0f;
                    }
                    if (loaded.tachoMaxKmh <= 20) {
                        loaded.tachoMaxKmh = 160;
                    }
                    if (loaded.blitzerDedupeRadiusBlocks <= 1.0) {
                        loaded.blitzerDedupeRadiusBlocks = 28.0;
                    }
                    if (loaded.plotCompanyName == null) {
                        loaded.plotCompanyName = "";
                    }
                    if (loaded.plotMatchRadiusBlocks <= 0) {
                        loaded.plotMatchRadiusBlocks = 150;
                    }
                    if (loaded.plotReminderDaysBefore <= 0) {
                        loaded.plotReminderDaysBefore = 7;
                    }
                    if (loaded.plotReminderIntervalMinutes < 0) {
                        loaded.plotReminderIntervalMinutes = 30;
                    }
                    if (loaded.plotWidgetScale <= 0.1f) {
                        loaded.plotWidgetScale = 1.0f;
                    }
                    // Validate and clamp critical configuration values
                    loaded.minimapZoomBlocks = Math.clamp(loaded.minimapZoomBlocks, 25.0, 1500.0);
                    loaded.minimapScale = Math.clamp(loaded.minimapScale, 0.2f, 3.0f);
                    loaded.tachoScale = Math.clamp(loaded.tachoScale, 0.2f, 3.0f);
                    loaded.speedCalibrationFactor = Math.clamp(loaded.speedCalibrationFactor, 0.5, 10.0);
                    loaded.warningDistanceBlocks = Math.clamp(loaded.warningDistanceBlocks, 20.0, 500.0);
                    loaded.blitzerDedupeRadiusBlocks = Math.clamp(loaded.blitzerDedupeRadiusBlocks, 5.0, 200.0);
                    loaded.firebaseSyncIntervalSeconds = Math.max(30, loaded.firebaseSyncIntervalSeconds);
                    return loaded;
                }
            } catch (Exception e) {
                System.err.println("[GM-Addon] Failed to read config.json: " + e.getMessage());
                try {
                    Path backupPath = configFile.resolveSibling("config.json.corrupted." + System.currentTimeMillis() + ".bak");
                    Files.copy(configFile, backupPath, StandardCopyOption.REPLACE_EXISTING);
                    System.err.println("[GM-Addon] Preserved corrupted config as backup: " + backupPath.getFileName());
                } catch (IOException backupEx) {
                    System.err.println("[GM-Addon] Failed to create backup of corrupted config: " + backupEx.getMessage());
                }
            }
        }
        AddonConfig defaults = new AddonConfig();
        defaults.save(configFile);
        return defaults;
    }

    public synchronized void save(Path configFile) {
        try {
            if (configFile.getParent() != null) {
                Files.createDirectories(configFile.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            System.err.println("[GM-Addon] Failed to save config.json: " + e.getMessage());
        }
    }
}
