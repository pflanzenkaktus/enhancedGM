package de.germanminer.addon.feature;

import de.germanminer.addon.config.AddonConfig;

import java.util.Locale;

/**
 * Tracks the player's Spawn / Respawn Protection ("Spawnschutz") countdown timer
 * (default 20 seconds, configurable in AddonConfig).
 */
public class SpawnProtectionTracker {
    private final AddonConfig config;
    private long startTimestampMs = 0L;
    private long durationMs = 0L;
    private String triggerReason = "Spawnschutz";

    public SpawnProtectionTracker(AddonConfig config) {
        this.config = config;
    }

    public void triggerOnServerJoin() {
        if (config.triggerSpawnProtectionOnJoin) {
            startTimer(config.spawnProtectionSeconds, "Login-Spawnschutz");
        }
    }

    public void triggerOnRespawn() {
        if (config.triggerSpawnProtectionOnRespawn) {
            startTimer(config.spawnProtectionSeconds, "Respawn-Schutz");
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
     * Optional chat listener to detect server-side Spawnschutz and hospital/revive protection messages.
     */
    public void onChatMessage(String plainMessage) {
        if (plainMessage == null || plainMessage.isBlank()) {
            return;
        }
        String lower = plainMessage.toLowerCase(Locale.ROOT);
        if (lower.contains("spawnschutz") && (lower.contains("beendet") || lower.contains("abgelaufen") || lower.contains("deaktiviert"))) {
            cancelTimer();
        } else if (lower.contains("spawnschutz") && (lower.contains("aktiv") || lower.contains("erhalten"))) {
            startTimer(config.spawnProtectionSeconds, "Spawnschutz");
        } else if (lower.contains("2 minuten keinen schaden erhalten")) {
            startTimer(120, "Krankenhaus-Schutz");
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
