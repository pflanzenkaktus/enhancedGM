package de.germanminer.addon;

import de.germanminer.addon.config.AddonConfig;
import de.germanminer.addon.data.SpeedCameraRepository;
import de.germanminer.addon.feature.BlitzerWarningTracker;
import de.germanminer.addon.feature.SpawnProtectionTracker;
import de.germanminer.addon.map.BlueMapMarkerClient;
import de.germanminer.addon.map.BlueMapTileClient;
import de.germanminer.addon.model.BlueMapMarker;
import de.germanminer.addon.model.SpeedCamera;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreSystemsTest {

    @Test
    void testBlueMapSignedCoordPathAndTileMath() {
        assertEquals("x0", BlueMapTileClient.formatSignedCoordPath("x", 0));
        assertEquals("x-1", BlueMapTileClient.formatSignedCoordPath("x", -1));
        assertEquals("z-4", BlueMapTileClient.formatSignedCoordPath("z", -4));
        assertEquals("x-1/2", BlueMapTileClient.formatSignedCoordPath("x", -12));
        assertEquals("z3/5", BlueMapTileClient.formatSignedCoordPath("z", 35));

        // Block (-1500, -1500) at LOD 1 (500 blocks per tile) -> tile -3
        assertEquals(-3, BlueMapTileClient.blockToTileCoord(-1500.0, 1));
        assertEquals(-1, BlueMapTileClient.blockToTileCoord(-1.0, 1));
        assertEquals(0, BlueMapTileClient.blockToTileCoord(250.0, 1));
        assertEquals(0.5, BlueMapTileClient.blockToTileUv(250.0, 0, 1), 1e-6);
    }

    @Test
    void testCropBlueMapDualLayerPngTo500x500() throws Exception {
        BufferedImage dual = new BufferedImage(501, 1002, BufferedImage.TYPE_INT_ARGB);
        dual.setRGB(10, 20, 0xFF123456);
        dual.setRGB(10, 600, 0xFFABCDEF); // metadata layer in bottom half
        ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
        ImageIO.write(dual, "PNG", rawOut);

        byte[] croppedBytes = BlueMapTileClient.cropBlueMapLowresTileToSquarePng(rawOut.toByteArray());
        assertNotNull(croppedBytes);

        BufferedImage cropped = ImageIO.read(new ByteArrayInputStream(croppedBytes));
        assertEquals(500, cropped.getWidth());
        assertEquals(500, cropped.getHeight());
        assertEquals(0xFF123456, cropped.getRGB(10, 20));
    }

    @Test
    void testFirebaseRealtimeDbAndFirestoreParsing(@TempDir Path tempDir) {
        String rtdbJson = """
            {
              "cam_hbf": {
                "name": "HBF Nord",
                "x": -885.5,
                "y": 63.0,
                "z": -2005.0,
                "speedLimitKmh": 70
              }
            }
            """;
        List<SpeedCamera> rtdbCams = SpeedCameraRepository.parseFirebaseJson(rtdbJson);
        assertEquals(1, rtdbCams.size());
        assertEquals("cam_hbf", rtdbCams.get(0).id());
        assertEquals(70, rtdbCams.get(0).speedLimitKmh());

        String firestoreJson = """
            {
              "documents": [
                {
                  "name": "projects/gm-addon/databases/(default)/documents/cameras/cam_altstadt",
                  "fields": {
                    "name": { "stringValue": "Altstadt Ost" },
                    "x": { "doubleValue": 15.0 },
                    "y": { "integerValue": "66" },
                    "z": { "doubleValue": -1310.0 },
                    "speedLimitKmh": { "integerValue": "50" }
                  }
                }
              ]
            }
            """;
        List<SpeedCamera> fsCams = SpeedCameraRepository.parseFirebaseJson(firestoreJson);
        assertEquals(1, fsCams.size());
        assertEquals("cam_altstadt", fsCams.get(0).id());
        assertEquals(50, fsCams.get(0).speedLimitKmh());

        AddonConfig cfg = new AddonConfig();
        cfg.blitzerDedupeRadiusBlocks = 28.0;
        Path dbFile = tempDir.resolve("speed_cameras.json");
        SpeedCameraRepository repo = new SpeedCameraRepository(dbFile, cfg);
        repo.initialize();
        int initialCount = repo.getCameras().size();
        assertTrue(initialCount >= 1);

        // Test chat flash message parsing
        var flash1 = BlitzerWarningTracker.parseFlashedChatMessage("§cDu wurdest mit 98 km/h in einer 70 Zone geblitzt.");
        assertTrue(flash1.isPresent());
        assertEquals(98, flash1.get().measuredKmh());
        assertEquals(70, flash1.get().speedLimitKmh());

        var flash2 = BlitzerWarningTracker.parseFlashedChatMessage("Du wurdest mit 115km/h in einer 80er-Zone geblitzt!");
        assertTrue(flash2.isPresent());
        assertEquals(115, flash2.get().measuredKmh());
        assertEquals(80, flash2.get().speedLimitKmh());

        // Ignore player chat / advertisements
        assertTrue(BlitzerWarningTracker.parseFlashedChatMessage("Globalchat [Zivilist] Test » Ich wurde geblitzt").isEmpty());

        // Test auto-recording and non-stacking deduplication
        var addRes1 = repo.recordAutoDetectedCamera(500.2, 64.0, -1200.4, 70, 90.0f, "GermanMinerPlayer");
        assertEquals(SpeedCameraRepository.AddCameraResult.ADDED_NEW, addRes1);
        assertEquals(initialCount + 1, repo.getCameras().size());

        SpeedCamera addedCam = repo.findNearbyCamera(500.0, 64.0, -1200.0).orElseThrow();
        assertTrue(addedCam.autoAdded());
        assertEquals("GermanMinerPlayer", addedCam.addedBy());
        assertEquals(70, addedCam.speedLimitKmh());

        // Second flash 8 blocks away at the same intersection -> must NOT stack!
        var addRes2 = repo.recordAutoDetectedCamera(506.0, 64.0, -1204.0, 70, 90.0f, "OtherPlayer");
        assertEquals(SpeedCameraRepository.AddCameraResult.MERGED_EXISTING, addRes2);
        assertEquals(initialCount + 1, repo.getCameras().size());
    }

    @Test
    void testBlitzerWarningApproachingAndMovingAway() {
        AddonConfig cfg = new AddonConfig();
        cfg.warningDistanceBlocks = 100.0;
        cfg.urgentDistanceBlocks = 40.0;
        cfg.directionConeDegrees = 60.0;

        BlitzerWarningTracker tracker = new BlitzerWarningTracker(cfg);
        SpeedCamera cam = new SpeedCamera("test", "Test Blitzer", 0.0, 64.0, 80.0, 70, null, 0.0);

        // Player at (0, 64, 0) looking South (+Z -> yaw 0) towards camera at (0, 64, 80)
        tracker.onActionBarMessage("Geschwindigkeit: 82 km/h");
        tracker.updatePlayerState(0.0, 64.0, 0.0, 0.0f, List.of(cam));
        BlitzerWarningTracker.BlitzerAlert alert = tracker.getCurrentAlert();
        assertNotNull(alert);
        assertEquals(80.0, alert.distanceBlocks(), 0.01);
        assertEquals(BlitzerWarningTracker.Urgency.OVER_SPEED_LIMIT, alert.urgency());

        // Player looking North (-Z -> yaw 180) away from the camera at 80 blocks distance
        tracker.updatePlayerState(0.0, 64.0, 0.0, 180.0f, List.of(cam));
        // When blitzerOnlyInVehicle is true and inVehicle is false, no alert is produced
        cfg.blitzerOnlyInVehicle = true;
        tracker.updatePlayerState(0.0, 64.0, 0.0, 0.0f, false, List.of(cam));
        assertNull(tracker.getCurrentAlert());

        // When sitting in a vehicle (inVehicle = true), alert is active again
        tracker.updatePlayerState(0.0, 64.0, 0.0, 0.0f, true, List.of(cam));
        assertNotNull(tracker.getCurrentAlert());

        // Test Tempomat hotkey state & chat parsing
        assertFalse(tracker.isTempomatActive());
        tracker.onTempomatToggleCommand();
        assertTrue(tracker.isTempomatActive());
        assertEquals(50, tracker.getTempomatSpeedKmh());

        tracker.onTempomatPlusCommand();
        assertEquals(60, tracker.getTempomatSpeedKmh());
        tracker.onTempomatMinusCommand();
        assertEquals(50, tracker.getTempomatSpeedKmh());

        tracker.onChatMessageForTempomat("§aDein Tempomat wurde auf 80 km/h eingestellt.");
        assertTrue(tracker.isTempomatActive());
        assertEquals(80, tracker.getTempomatSpeedKmh());

        tracker.onChatMessageForTempomat("§cTempomat deaktiviert.");
        assertFalse(tracker.isTempomatActive());

        // Test brake auto-disengage ('S')
        tracker.onTempomatToggleCommand();
        assertTrue(tracker.isTempomatActive());
        tracker.disengageTempomatOnBrake();
        assertFalse(tracker.isTempomatActive());

        // Test vehicle unsupported detection via chat
        assertTrue(tracker.isTempomatAvailable());
        tracker.onChatMessageForTempomat("§cDieses Fahrzeug besitzt keinen Tempomat!");
        assertFalse(tracker.isTempomatAvailable());
        assertFalse(tracker.isTempomatActive());

        // When unavailable, toggle command should not activate
        tracker.onTempomatToggleCommand();
        assertFalse(tracker.isTempomatActive());

        // Supported vehicle message re-enables availability
        tracker.onChatMessageForTempomat("§aTempomat auf 60 km/h aktiviert.");
        assertTrue(tracker.isTempomatAvailable());
        assertTrue(tracker.isTempomatActive());
        assertEquals(60, tracker.getTempomatSpeedKmh());

        // Another unsupported variant
        tracker.onChatMessageForTempomat("§cTempomat ist für dieses Fahrzeug nicht verfügbar.");
        assertFalse(tracker.isTempomatAvailable());
        assertFalse(tracker.isTempomatActive());

        // Leaving vehicle resets availability for next vehicle
        tracker.updatePlayerState(0.0, 64.0, 0.0, 0.0f, false, List.of(cam));
        assertTrue(tracker.isTempomatAvailable());
    }

    @Test
    void testDamageLogParsingAnd30sNotwehrEvaluation() {
        AddonConfig cfg = new AddonConfig();
        cfg.notwehrWindowSeconds = 30;
        de.germanminer.addon.feature.DamageLogTracker tracker = new de.germanminer.addon.feature.DamageLogTracker(cfg);

        assertTrue(tracker.onChatMessage("§e§l»§7 §6♺§e Bitte warte, dein Damage-Log wird vorbereitet...", "PflanzenKaktus"));
        assertTrue(tracker.onChatMessage("            »  Damage-Log von PflanzenKaktus  «", "PflanzenKaktus"));
        assertTrue(tracker.onChatMessage("          §7§o(§cErlittener Schaden §7§o//§a Zugefügter Schaden§7§o)", "PflanzenKaktus"));
        // Exact lines from GermanMiner server log + simulated counter-attacks
        assertTrue(tracker.onChatMessage("§700:26:23 §c● §cHerrSieger §7» §cPflanzenKaktus §7§oDMG", "PflanzenKaktus"));
        assertTrue(tracker.onChatMessage("§700:41:32 §c● §cHerrSieger §7» §cPflanzenKaktus §7§oDMG", "PflanzenKaktus"));
        // Counter-attack within 12s (<= 30s -> VALID_NOTWEHR)
        assertTrue(tracker.onChatMessage("§700:41:44 §a● §aPflanzenKaktus §7» §aHerrSieger §7§oDMG", "PflanzenKaktus"));
        // Late attack against another player 45s after incoming hit (> 30s -> EXPIRED_NOTWEHR)
        assertTrue(tracker.onChatMessage("§700:42:00 §c● §cBandito99 §7» §cPflanzenKaktus §7§oDMG", "PflanzenKaktus"));
        assertTrue(tracker.onChatMessage("§700:42:45 §a● §aPflanzenKaktus §7» §aBandito99 §7§oDMG", "PflanzenKaktus"));
        // Unprovoked first strike against a third player (-> FIRST_STRIKE_BY_SELF)
        assertTrue(tracker.onChatMessage("§700:43:10 §a● §aPflanzenKaktus §7» §aUnschuldig §7§oDMG", "PflanzenKaktus"));

        var entries = tracker.getEvaluatedEntries();
        assertEquals(6, entries.size());

        // Entry 2 (index 2): 00:41:44 PflanzenKaktus » HerrSieger (12s after 00:41:32) -> VALID_NOTWEHR
        assertEquals(de.germanminer.addon.feature.DamageLogTracker.NotwehrStatus.VALID_NOTWEHR, entries.get(2).status());
        assertEquals(12, entries.get(2).deltaSecondsFromIncoming());

        // Entry 4 (index 4): 00:42:45 PflanzenKaktus » Bandito99 (45s after 00:42:00) -> EXPIRED_NOTWEHR
        assertEquals(de.germanminer.addon.feature.DamageLogTracker.NotwehrStatus.EXPIRED_NOTWEHR, entries.get(4).status());
        assertEquals(45, entries.get(4).deltaSecondsFromIncoming());

        // Entry 5 (index 5): 00:43:10 PflanzenKaktus » Unschuldig -> FIRST_STRIKE_BY_SELF
        assertEquals(de.germanminer.addon.feature.DamageLogTracker.NotwehrStatus.FIRST_STRIKE_BY_SELF, entries.get(5).status());

        var summaries = tracker.getOpponentSummaries();
        assertEquals(3, summaries.size());
        assertEquals(de.germanminer.addon.feature.DamageLogTracker.NotwehrStatus.VALID_NOTWEHR, summaries.get(0).overallStatus());
        assertEquals(de.germanminer.addon.feature.DamageLogTracker.NotwehrStatus.EXPIRED_NOTWEHR, summaries.get(1).overallStatus());
        assertEquals(de.germanminer.addon.feature.DamageLogTracker.NotwehrStatus.FIRST_STRIKE_BY_SELF, summaries.get(2).overallStatus());
    }

    @Test
    void testSpawnProtectionCountdown() {
        AddonConfig cfg = new AddonConfig();
        cfg.spawnProtectionSeconds = 20;
        SpawnProtectionTracker tracker = new SpawnProtectionTracker(cfg);
        assertFalse(tracker.isActive());

        tracker.triggerOnServerJoin();
        assertTrue(tracker.isActive());
        assertTrue(tracker.getRemainingSeconds() > 19.0 && tracker.getRemainingSeconds() <= 20.0);

        tracker.onChatMessage("[Server] Dein Spawnschutz ist abgelaufen!");
        assertFalse(tracker.isActive());
    }

    @Test
    void testBlueMapMarkersParsing() {
        String sample = """
            {
              "Tankstellen": {
                "label": "Tankstellen",
                "markers": {
                  "t1": {
                    "type": "poi",
                    "label": "Tankstelle HBF",
                    "detail": "Tankstelle HBF",
                    "position": { "x": -800.0, "y": 63.0, "z": -1900.0 }
                  }
                }
              }
            }
            """;
        List<BlueMapMarker> markers = BlueMapMarkerClient.parseMarkersJson(sample);
        assertEquals(1, markers.size());
        assertEquals("Tankstellen", markers.get(0).categoryId());
        assertEquals("Tankstelle HBF", markers.get(0).label());
    }
}
