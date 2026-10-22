package de.pflanzenkaktus.enhancedgm;

import de.pflanzenkaktus.enhancedgm.config.AddonConfig;
import de.pflanzenkaktus.enhancedgm.data.SpeedCameraRepository;
import de.pflanzenkaktus.enhancedgm.feature.BlitzerWarningTracker;
import de.pflanzenkaktus.enhancedgm.feature.SpawnProtectionTracker;
import de.pflanzenkaktus.enhancedgm.map.BlueMapMarkerClient;
import de.pflanzenkaktus.enhancedgm.map.BlueMapTileClient;
import de.pflanzenkaktus.enhancedgm.model.BlueMapMarker;
import de.pflanzenkaktus.enhancedgm.model.SpeedCamera;
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

        // Verify Community Cloud Sync default behavior
        assertFalse(cfg.forceEnableEverywhere, "forceEnableEverywhere must default to false for release safety");
        assertFalse(cfg.enableFirebaseCloudSync, "Firebase sync must be disabled by default");
        assertFalse(repo.hasFirebaseUrl(), "Repository must not have an active cloud URL when sync is disabled");
        assertTrue(repo.getSyncStatus().contains("Cloud-Sync"));

        cfg.enableFirebaseCloudSync = true;
        assertTrue(repo.hasFirebaseUrl(), "Repository must activate cloud URL once user opts in");

        // Test chat flash message parsing
        var flash1 = BlitzerWarningTracker.parseFlashedChatMessage("§cDu wurdest mit 98 km/h in einer 70 Zone geblitzt.");
        assertTrue(flash1.isPresent());
        assertEquals(98, flash1.get().measuredKmh());
        assertEquals(70, flash1.get().speedLimitKmh());

        var flash2 = BlitzerWarningTracker.parseFlashedChatMessage("Du wurdest mit 115km/h in einer 80er-Zone geblitzt!");
        assertTrue(flash2.isPresent());
        assertEquals(115, flash2.get().measuredKmh());
        assertEquals(80, flash2.get().speedLimitKmh());

        // Real GermanMiner server notification with '» ℹ' prefix
        var flashRealServer = BlitzerWarningTracker.parseFlashedChatMessage("§b§l»§7§l §fℹ§b Du wurdest mit §l111 km/h §bin einer §l70 km/h-Zone§b geblitzt. Du hast ein Bußgeld in Höhe von 75,00 Euro und 1 Punkt erhalten.");
        assertTrue(flashRealServer.isPresent());
        assertEquals(111, flashRealServer.get().measuredKmh());
        assertEquals(70, flashRealServer.get().speedLimitKmh());

        // Ignore player chat / advertisements / spoof attempts
        assertTrue(BlitzerWarningTracker.parseFlashedChatMessage("Globalchat [Zivilist] Test » Ich wurde geblitzt").isEmpty());
        assertTrue(BlitzerWarningTracker.parseFlashedChatMessage("Max » Du wurdest mit 98 km/h in einer 70 Zone geblitzt.").isEmpty());
        assertTrue(BlitzerWarningTracker.parseFlashedChatMessage("Troll » §cDu wurdest mit 120 km/h in einer 70 Zone geblitzt.").isEmpty());

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

        // Test ETag tracking & cache interval
        assertNull(repo.getLastETag());
        repo.setLastETag("\"test-etag-123\"");
        assertEquals("\"test-etag-123\"", repo.getLastETag());
        assertEquals(300, cfg.firebaseSyncIntervalSeconds);
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

        // Test spoofing protection
        tracker.triggerOnServerJoin();
        assertTrue(tracker.isActive());
        tracker.onChatMessage("Globalchat [Zivilist] Max » Spawnschutz beendet!");
        assertTrue(tracker.isActive(), "Player chat message must not cancel Spawnschutz timer");
        tracker.onChatMessage("Troll » Dein Spawnschutz ist abgelaufen!");
        assertTrue(tracker.isActive(), "Player chat message with '»' must not cancel Spawnschutz timer");
        tracker.onChatMessage("Dein Spawnschutz ist abgelaufen!");
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

    @Test
    void testSpeedSmoothingAndLagSpikeDamping() {
        AddonConfig cfg = new AddonConfig();
        BlitzerWarningTracker tracker = new BlitzerWarningTracker(cfg);

        // Simulate steady ~50 km/h driving (~18.5 blocks/sec, dx = 0.925 blocks per 0.05s tick)
        double x = 0.0;
        tracker.updatePlayerState(x, 64.0, 0.0, 0.0f, true, List.of());
        for (int i = 0; i < 25; i++) {
            x += 0.925;
            tracker.updatePlayerState(x, 64.0, 0.0, 0.0f, true, List.of());
        }
        double steadySpeed = tracker.getCurrentSpeedKmh();
        assertTrue(steadySpeed >= 40.0 && steadySpeed <= 55.0, "Speed should be around 50 km/h, got: " + steadySpeed);

        // Simulate network lag: 1 packet delayed (0 movement in tick)
        tracker.updatePlayerState(x, 64.0, 0.0, 0.0f, true, List.of());
        double speedDuringLag = tracker.getCurrentSpeedKmh();
        assertTrue(speedDuringLag >= 30.0, "Speed should remain stable during 1 dropped packet, got: " + speedDuringLag);

        // Catch-up burst packet from server: double distance in next tick
        x += 2.5;
        tracker.updatePlayerState(x, 64.0, 0.0, 0.0f, true, List.of());
        double speedAfterBurst = tracker.getCurrentSpeedKmh();
        assertTrue(speedAfterBurst <= 65.0, "Speed should not spike violently due to anti-spike clamp, got: " + speedAfterBurst);
    }
}
