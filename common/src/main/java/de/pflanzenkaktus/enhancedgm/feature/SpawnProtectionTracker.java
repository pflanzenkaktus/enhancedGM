package de.pflanzenkaktus.enhancedgm.feature;

import de.pflanzenkaktus.enhancedgm.config.AddonConfig;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tracks the player's Spawn / Respawn Protection ("Spawnschutz") countdown timer
 * (default 20 seconds, configurable in AddonConfig).
 */
public class SpawnProtectionTracker {
    private static final Pattern COLOR_STRIP_PATTERN = Pattern.compile("[§&][0-9a-fk-orxA-FK-ORX]");

    private final AddonConfig config;
    private long startTimestampMs = 0L;
    private long durationMs = 0L;
    private String triggerReason = "Spawnschutz";

    public SpawnProtectionTracker(AddonConfig config) {
        this.config = config;
    }

    public void triggerOnServerJoin() {
        if (config.triggerSpawnProtectionOnJoin) {
            long targetMs = config.spawnProtectionSeconds * 1000L;
            if (getRemainingMillis() < targetMs) {
                startTimer(config.spawnProtectionSeconds, "Login-Spawnschutz");
            }
        }
    }

    public void triggerOnRespawn() {
        if (config.triggerSpawnProtectionOnRespawn) {
            long targetMs = config.spawnProtectionSeconds * 1000L;
            if (getRemainingMillis() < targetMs) {
                startTimer(config.spawnProtectionSeconds, "Respawn-Schutz");
            }
        }
    }

    public void startTimer(int seconds, String reason) {
        if (seconds <= 0) {
            return;
        }
        this.startTimestampMs = System.currentTimeMillis();
        this.durationMs = seconds * 1000L;
        this.triggerReason = (reason != null && !reason.isBlank()) ? reason : "Spawnschutz";
    }

    public void cancelTimer() {
        this.durationMs = 0L;
    }

    /**
     * Chat listener to detect server-side Spawnschutz and hospital/revive protection messages.
     */
    public void onChatMessage(String plainMessage) {
        if (plainMessage == null || plainMessage.isBlank()) {
            return;
        }
        String clean = COLOR_STRIP_PATTERN.matcher(plainMessage).replaceAll("").trim();
        String lower = clean.toLowerCase(Locale.ROOT);

        // Ignore player chat channels, broadcasts, and player-authored lines with a name before '»'
        if (lower.contains("globalchat") || lower.contains("[zivilist]") || lower.contains("[bandito]")
            || lower.contains("megafon") || lower.contains("werbung")
            || lower.contains("[lokal]") || lower.contains("[funk]")) {
            return;
        }
        if (clean.contains("»")) {
            String prefix = clean.substring(0, clean.indexOf('»')).trim();
            if (!prefix.isEmpty() && prefix.matches(".*[a-zA-ZäöüÄÖÜß].*")) {
                return;
            }
        }

        if (lower.contains("nicht mehr im pvp-schutz")
            || (lower.contains("spawnschutz") && (lower.contains("beendet") || lower.contains("abgelaufen") || lower.contains("deaktiviert")))) {
            cancelTimer();
        } else if (lower.contains("2 minuten keinen schaden erhalten")
            || (lower.contains("krankenhaus gebracht") && lower.contains("keinen schaden"))) {
            startTimer(120, "Krankenhaus-Schutz");
        } else if (lower.contains("spawnschutz") && (lower.contains("aktiv") || lower.contains("erhalten"))) {
            startTimer(config.spawnProtectionSeconds, "Spawnschutz");
        }
    }

    public boolean isActive() {
        return getRemainingMillis() > 0L;
    }

    public long getRemainingMillis() {
        if (durationMs <= 0L) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - startTimestampMs;
        return Math.max(0L, durationMs - elapsed);
    }

    public double getRemainingSeconds() {
        return getRemainingMillis() / 1000.0;
    }

    public float getProgressRatio() {
        if (durationMs <= 0L) {
            return 0.0f;
        }
        return Math.clamp((float) getRemainingMillis() / (float) durationMs, 0.0f, 1.0f);
    }

    public String getTriggerReason() {
        return triggerReason;
    }
}
