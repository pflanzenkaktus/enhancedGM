package de.germanminer.addon.feature;

import de.germanminer.addon.config.AddonConfig;
import de.germanminer.addon.model.SpeedCamera;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates player proximity, movement velocity, and heading toward speed cameras ("Blitzer")
 * to trigger multi-stage visual and audio warnings.
 */
public class BlitzerWarningTracker {
    private static final Pattern KMH_PATTERN = Pattern.compile("(\\d{1,3}(?:[.,]\\d+)?)(?:\\s*/\\s*\\d{1,3})?\\s*km/h", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_STRIP_PATTERN = Pattern.compile("(?i)§[0-9A-FK-ORX]");

    // Matches e.g. "Tempomat: 70 km/h", "Tempomat auf 70 km/h", "Tempomat (70 km/h)"
    private static final Pattern TEMPOMAT_SPEED_PATTERN = Pattern.compile(
        "tempomat[^0-9\\n]{0,25}(\\d{1,3})\\s*(?:km/h)?",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TEMPOMAT_OFF_PATTERN = Pattern.compile(
        "tempomat.*(?:deaktiviert|ausgeschaltet|aus\\b|off\\b|beendet)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TEMPOMAT_ON_PATTERN = Pattern.compile(
        "tempomat.*(?:aktiviert|eingeschaltet|an\\b|on\\b|gesetzt|erhöht|reduziert|gestellt)",
        Pattern.CASE_INSENSITIVE
    );

    // Matches e.g. "in einer 70 Zone", "in einer 70er-Zone", "in einer 70 km/h Zone", "Erlaubt: 70"
    private static final Pattern ZONE_LIMIT_PATTERN = Pattern.compile(
        "(?:in\\s+einer\\s+|Zone\\s*:?\\s*|Erlaubt\\s*:?\\s*|Tempolimit\\s*:?\\s*|Limit\\s*:?\\s*)(\\d{2,3})\\s*(?:er|km/h)?(?:[\\s-]*Zone)?",
        Pattern.CASE_INSENSITIVE
    );
    // Matches e.g. "mit 95 km/h", "mit 95km/h"
    private static final Pattern MEASURED_SPEED_PATTERN = Pattern.compile(
        "mit\\s+(\\d{1,3}(?:[.,]\\d+)?)\\s*(?:km/h)?",
        Pattern.CASE_INSENSITIVE
    );

    public record FlashedEvent(
        int measuredKmh,
        int speedLimitKmh
    ) {}

    public enum Urgency {
        NONE,
        APPROACHING,
        URGENT,
        OVER_SPEED_LIMIT
    }

    public record BlitzerAlert(
        SpeedCamera camera,
        double distanceBlocks,
        double currentSpeedKmh,
        int speedLimitKmh,
        Urgency urgency,
        double relativeBearingDegrees
    ) {}

    private final AddonConfig config;

    private double prevX = Double.NaN;
    private double prevZ = Double.NaN;
    private long prevUpdateTimeMs = 0L;
    private double smoothedSpeedBlocksPerSec = 0.0;
    private Double serverReportedKmh = null;
    private long serverReportedKmhTimestamp = 0L;

    private boolean inVehicleState = false;
    private boolean tempomatActive = false;
    private int tempomatSpeedKmh = 50;

    private BlitzerAlert currentAlert = null;
    private String lastSoundAlertCameraId = null;
    private long lastSoundAlertTimeMs = 0L;
    private boolean pendingWarningSound = false;
    private float pendingWarningSoundPitch = 1.0f;

    public BlitzerWarningTracker(AddonConfig config) {
        this.config = config;
    }

    /**
     * Parses a GermanMiner chat line to detect if the player was just flashed by a speed camera
     * (e.g., {@code "Du wurdest mit 95 km/h in einer 70 Zone geblitzt."}).
     */
    public static Optional<FlashedEvent> parseFlashedChatMessage(String rawOrPlainText) {
        if (rawOrPlainText == null || rawOrPlainText.isBlank()) {
            return Optional.empty();
        }
        String clean = COLOR_STRIP_PATTERN.matcher(rawOrPlainText).replaceAll("").trim();
        String lower = clean.toLowerCase();

        if (!lower.contains("geblitzt")) {
            return Optional.empty();
        }
        // Ignore player chat / globalchat / radio / megaphone messages
        if (lower.contains("globalchat") || lower.contains("[zivilist]") || lower.contains("[bandito]")
            || lower.contains("megafon") || lower.contains("werbung")) {
            return Optional.empty();
        }
        if (!lower.contains("du wurdest") && !lower.contains("km/h") && !lower.contains("zone")) {
            return Optional.empty();
        }

        int measuredKmh = 0;
        Matcher speedMatcher = MEASURED_SPEED_PATTERN.matcher(clean);
        if (speedMatcher.find()) {
            try {
                measuredKmh = (int) Math.round(Double.parseDouble(speedMatcher.group(1).replace(',', '.')));
            } catch (NumberFormatException ignored) {
            }
        } else {
            Matcher fallbackKmh = KMH_PATTERN.matcher(clean);
            if (fallbackKmh.find()) {
                try {
                    measuredKmh = (int) Math.round(Double.parseDouble(fallbackKmh.group(1).replace(',', '.')));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        int limitKmh = 70; // Default GermanMiner city speed limit
        Matcher zoneMatcher = ZONE_LIMIT_PATTERN.matcher(clean);
        if (zoneMatcher.find()) {
            try {
                int parsedZone = Integer.parseInt(zoneMatcher.group(1));
                if (parsedZone >= 10 && parsedZone <= 250) {
                    limitKmh = parsedZone;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return Optional.of(new FlashedEvent(measuredKmh, limitKmh));
    }

    /**
     * Parses chat, bossbar, actionbar, or GUI item text for Tempomat status updates from GermanMiner's vehicle system.
     */
    public void onChatMessageForTempomat(String rawOrPlainText) {
        if (rawOrPlainText == null || rawOrPlainText.isBlank()) {
            return;
        }
        String clean = COLOR_STRIP_PATTERN.matcher(rawOrPlainText).replaceAll("").trim();
        String lower = clean.toLowerCase();
        if (!lower.contains("tempomat")) {
            return;
        }
        boolean explicitlyOff = TEMPOMAT_OFF_PATTERN.matcher(clean).find()
            || lower.contains("deaktiviert")
            || lower.contains("ausgeschaltet")
            || lower.contains(": aus")
            || lower.contains("(aus)");
        boolean explicitlyOn = TEMPOMAT_ON_PATTERN.matcher(clean).find()
            || lower.contains("aktiviert")
            || lower.contains("eingeschaltet")
            || lower.contains(": an")
            || lower.contains("(an)")
            || lower.contains("aktiv");

        Matcher speedMatcher = TEMPOMAT_SPEED_PATTERN.matcher(clean);
        if (speedMatcher.find()) {
            try {
                int parsed = Integer.parseInt(speedMatcher.group(1));
                if (parsed >= 5 && parsed <= 300) {
                    tempomatSpeedKmh = parsed;
                    if (explicitlyOff) {
                        tempomatActive = false;
                    } else if (explicitlyOn || !lower.contains("status")) {
                        tempomatActive = true;
                    }
                    return;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (explicitlyOff) {
            tempomatActive = false;
        } else if (explicitlyOn) {
            tempomatActive = true;
        }
    }

    /**
     * Directly synchronizes Tempomat state read from the server-provided vehicle GUI menu or HUD.
     */
    public void syncTempomatFromServerUi(Boolean active, Integer speedKmh) {
        if (speedKmh != null && speedKmh >= 5 && speedKmh <= 300) {
            this.tempomatSpeedKmh = speedKmh;
        }
        if (active != null) {
            this.tempomatActive = active;
        }
    }

    /**
     * Optional hook for actionbar or bossbar messages (e.g., vehicle speedometer on GermanMiner).
     */
    public void onActionBarMessage(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return;
        }
        String clean = COLOR_STRIP_PATTERN.matcher(plainText).replaceAll("").trim();
        onChatMessageForTempomat(clean);

        // Remove any "Tempomat: XX km/h" segment before matching actual driving speed
        String withoutTempomat = TEMPOMAT_SPEED_PATTERN.matcher(clean).replaceAll("");
        Matcher matcher = KMH_PATTERN.matcher(withoutTempomat);
        if (matcher.find()) {
            try {
                String num = matcher.group(1).replace(',', '.');
                serverReportedKmh = Double.parseDouble(num);
                serverReportedKmhTimestamp = System.currentTimeMillis();
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public void onTempomatToggleCommand() {
        tempomatActive = !tempomatActive;
        if (tempomatActive && tempomatSpeedKmh <= 0) {
            int currentRounded = (int) Math.round(getCurrentSpeedKmh() / 10.0) * 10;
            tempomatSpeedKmh = Math.clamp(currentRounded > 0 ? currentRounded : 50, 10, 250);
        }
    }

    public void onTempomatPlusCommand() {
        if (!tempomatActive) {
            tempomatActive = true;
        }
        tempomatSpeedKmh = Math.min(250, tempomatSpeedKmh + 10);
    }

    public void onTempomatMinusCommand() {
        if (!tempomatActive) {
            tempomatActive = true;
        }
        tempomatSpeedKmh = Math.max(10, tempomatSpeedKmh - 10);
    }

    public boolean isTempomatActive() {
        return tempomatActive;
    }

    public int getTempomatSpeedKmh() {
        return tempomatSpeedKmh;
    }

    public boolean isInVehicle() {
        return inVehicleState;
    }

    /**
     * Updates player telemetry and computes the most urgent approaching SpeedCamera alert.
     */
    public void updatePlayerState(double x, double y, double z, float playerYaw, List<SpeedCamera> cameras) {
        updatePlayerState(x, y, z, playerYaw, true, cameras);
    }

    /**
     * Updates player telemetry and computes the most urgent approaching SpeedCamera alert.
     * Uses fixed 20 TPS tick delta when called on client ticks for instant, jitter-free speed updates.
     */
    public void updatePlayerState(double x, double y, double z, float playerYaw, boolean inVehicle, List<SpeedCamera> cameras) {
        if (this.inVehicleState && !inVehicle) {
            this.tempomatActive = false;
        }
        this.inVehicleState = inVehicle;

        long now = System.currentTimeMillis();
        double vx = 0.0;
        double vz = 0.0;

        if (!Double.isNaN(prevX) && prevUpdateTimeMs > 0) {
            long elapsedMs = now - prevUpdateTimeMs;
            // Use exact 0.05s (20 TPS) when called from client tick to eliminate frame-time jitter,
            // or wall-clock dt if ticks were skipped.
            double dtSec = (elapsedMs >= 20L && elapsedMs <= 90L) ? 0.05 : (elapsedMs / 1000.0);
            if (dtSec > 0.005 && dtSec < 2.0) {
                vx = (x - prevX) / dtSec;
                vz = (z - prevZ) / dtSec;
                double instSpeed = Math.hypot(vx, vz);
                if (instSpeed < 100.0) { // ignore teleports
                    if (instSpeed < 0.05) {
                        // Snap quickly to 0 when stopped
                        smoothedSpeedBlocksPerSec = smoothedSpeedBlocksPerSec < 0.25 ? 0.0 : smoothedSpeedBlocksPerSec * 0.35;
                    } else {
                        // Fast response (70% new, 30% previous) for immediate acceleration/braking feedback
                        smoothedSpeedBlocksPerSec = smoothedSpeedBlocksPerSec * 0.30 + instSpeed * 0.70;
                    }
                } else {
                    smoothedSpeedBlocksPerSec = 0.0;
                }
            }
        }
        prevX = x;
        prevZ = z;
        prevUpdateTimeMs = now;

        if (config.blitzerOnlyInVehicle && !inVehicle) {
            this.currentAlert = null;
            this.lastSoundAlertCameraId = null;
            this.pendingWarningSound = false;
            return;
        }

        double speedKmh = getCurrentSpeedKmh();

        // Determine movement heading (or look yaw if moving slowly)
        double moveHeadingYaw = playerYaw;
        double horizSpeed = Math.sqrt(vx * vx + vz * vz);
        if (horizSpeed >= config.minSpeedBlocksPerSec) {
            // In Minecraft: yaw 0 is +Z (South), yaw -90 is +X (East), yaw 90 is -X (West), yaw 180 is -Z (North)
            moveHeadingYaw = Math.toDegrees(Math.atan2(-vx, vz));
        }

        final double headingYawFinal = moveHeadingYaw;
        Optional<BlitzerAlert> bestAlert = cameras.stream()
            .map(cam -> evaluateCamera(cam, x, y, z, headingYawFinal, playerYaw, speedKmh))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .min(Comparator.comparingDouble(BlitzerAlert::distanceBlocks));

        this.currentAlert = bestAlert.orElse(null);

        if (this.currentAlert != null && config.playWarningSound) {
            String camId = this.currentAlert.camera().id();
            boolean isNewCamera = !camId.equals(lastSoundAlertCameraId);
            boolean urgentRepeat = (this.currentAlert.urgency() == Urgency.OVER_SPEED_LIMIT)
                && (now - lastSoundAlertTimeMs > 2200L);

            if (isNewCamera || urgentRepeat) {
                pendingWarningSound = true;
                pendingWarningSoundPitch = (this.currentAlert.urgency() == Urgency.OVER_SPEED_LIMIT) ? 1.5f : 1.15f;
                lastSoundAlertCameraId = camId;
                lastSoundAlertTimeMs = now;
            }
        } else if (this.currentAlert == null) {
            lastSoundAlertCameraId = null;
        }
    }

    private Optional<BlitzerAlert> evaluateCamera(
        SpeedCamera cam,
        double px,
        double py,
        double pz,
        double movementYaw,
        double lookYaw,
        double speedKmh
    ) {
        double maxDist = cam.triggerRadius() > 0 ? cam.triggerRadius() : config.warningDistanceBlocks;
        double dist = cam.horizontalDistanceTo(px, pz);
        if (dist > maxDist || Math.abs(cam.y() - py) > 45.0) {
            return Optional.empty();
        }

        // Bearing from player to camera in Minecraft yaw degrees
        double dx = cam.x() - px;
        double dz = cam.z() - pz;
        double bearingToCameraYaw = Math.toDegrees(Math.atan2(-dx, dz));

        double angleDiffMove = Math.abs(wrapDegrees(bearingToCameraYaw - movementYaw));
        double angleDiffLook = Math.abs(wrapDegrees(bearingToCameraYaw - lookYaw));
        double effectiveAngleDiff = Math.min(angleDiffMove, angleDiffLook);

        // Always warn if very close (< 22 blocks), or if within the approaching cone
        if (dist > 22.0 && effectiveAngleDiff > config.directionConeDegrees) {
            return Optional.empty();
        }

        int limit = cam.effectiveSpeedLimit();
        Urgency urgency;
        if (speedKmh > limit) {
            urgency = Urgency.OVER_SPEED_LIMIT;
        } else if (dist <= config.urgentDistanceBlocks) {
            urgency = Urgency.URGENT;
        } else {
            urgency = Urgency.APPROACHING;
        }

        double relBearing = wrapDegrees(bearingToCameraYaw - lookYaw);
        return Optional.of(new BlitzerAlert(cam, dist, speedKmh, limit, urgency, relBearing));
    }

    public double getCurrentSpeedKmh() {
        double factor = config.speedCalibrationFactor > 0.1 ? config.speedCalibrationFactor : 2.70;
        double liveSpeedKmh = smoothedSpeedBlocksPerSec * factor;
        if (liveSpeedKmh > 0.5) {
            return liveSpeedKmh;
        }
        long now = System.currentTimeMillis();
        if (serverReportedKmh != null && (now - serverReportedKmhTimestamp) < 400L) {
            return serverReportedKmh;
        }
        return liveSpeedKmh;
    }

    public double getSmoothedSpeedBlocksPerSec() {
        return smoothedSpeedBlocksPerSec;
    }

    public BlitzerAlert getCurrentAlert() {
        return currentAlert;
    }

    public boolean consumePendingWarningSound() {
        if (pendingWarningSound) {
            pendingWarningSound = false;
            return true;
        }
        return false;
    }

    public float getPendingWarningSoundPitch() {
        return pendingWarningSoundPitch;
    }

    public static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) {
            d -= 360.0;
        }
        if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }
}
