package de.pflanzenkaktus.enhancedgm.feature;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tracks whether the player is currently in the GermanMiner Farmwelt, another non-main dimension,
 * or on a non-GermanMiner server so the minimap automatically switches to a fair surface-only view.
 */
public class WorldContextTracker {
    private static final long LEVEL_CHANGE_GRACE_MS = 3500L;
    private static final Pattern COLOR_STRIP_PATTERN = Pattern.compile("§[0-9a-fk-orxA-FK-ORX]");

    private volatile boolean chatDetectedFarmWorld = false;
    private volatile boolean scoreboardFarmWorld = false;
    private volatile boolean nonMainDimension = false;
    private volatile long lastFarmWorldChatMs = 0L;

    public void onChatMessage(String rawOrPlainText) {
        onChatMessage(rawOrPlainText, System.currentTimeMillis());
    }

    public void onChatMessage(String rawOrPlainText, long nowMs) {
        if (rawOrPlainText == null || rawOrPlainText.isBlank()) {
            return;
        }
        String clean = COLOR_STRIP_PATTERN.matcher(rawOrPlainText).replaceAll("").trim();
        String lower = clean.toLowerCase(Locale.ROOT);

        if (lower.contains("globalchat") || lower.contains("[zivilist]") || lower.contains("[bandito]")
            || lower.contains("megafon") || lower.contains("werbung")) {
            return;
        }
        if (clean.contains("»")) {
            String prefix = clean.substring(0, clean.indexOf('»')).trim();
            if (!prefix.isEmpty() && prefix.matches(".*[a-zA-ZäöüÄÖÜß].*")) {
                return;
            }
        }

        if (lower.contains("du befindest dich nun in der farmwelt")
            || lower.contains("mit dem befehl /rückflug kommst du wieder in die hauptwelt")
            || lower.contains("es ist ab hier möglich, blöcke abzubauen")) {
            this.chatDetectedFarmWorld = true;
            this.lastFarmWorldChatMs = nowMs;
            return;
        }

        if (lower.contains("du befindest dich nun in der hauptwelt")
            || lower.contains("willkommen in der hauptwelt")
            || lower.contains("zurück in der hauptwelt")
            || lower.contains("willkommen auf")) {
            this.chatDetectedFarmWorld = false;
        }
    }

    public void onScoreboardText(String combinedText) {
        if (combinedText == null || combinedText.isBlank()) {
            this.scoreboardFarmWorld = false;
            return;
        }
        String clean = COLOR_STRIP_PATTERN.matcher(combinedText).replaceAll("");
        String lower = clean.toLowerCase(Locale.ROOT);
        boolean mentionsReset = lower.contains("farmweltreset")
            || lower.contains("farmwelt-reset")
            || lower.contains("farmwelt reset");
        this.scoreboardFarmWorld = !mentionsReset && (lower.contains("farmwelt") || lower.contains("farm-welt"));
    }

    public void onDimensionState(boolean isOverworld, String dimensionName) {
        boolean wasNonMain = this.nonMainDimension;
        String dimId = extractDimensionId(dimensionName);
        boolean nextNonMain = !isOverworld
            || dimId.contains("farmwelt")
            || dimId.contains("farm")
            || dimId.contains("mining")
            || dimId.contains("resource_world")
            || dimId.contains("resourceworld");
        this.nonMainDimension = nextNonMain;
        if (wasNonMain && !nextNonMain) {
            // Dimension explicitly switched back to the main Overworld (e.g., via /rückflug)
            this.chatDetectedFarmWorld = false;
        }
    }

    private static String extractDimensionId(String dimensionName) {
        if (dimensionName == null || dimensionName.isBlank()) {
            return "";
        }
        String clean = dimensionName.trim();
        int slashIdx = clean.lastIndexOf('/');
        if (slashIdx >= 0 && slashIdx + 1 < clean.length()) {
            clean = clean.substring(slashIdx + 1).trim();
        }
        if (clean.endsWith("]")) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }
        return clean.toLowerCase(Locale.ROOT);
    }

    public void onLevelInstanceChanged(long nowMs) {
        if ((nowMs - this.lastFarmWorldChatMs) > LEVEL_CHANGE_GRACE_MS) {
            this.chatDetectedFarmWorld = false;
        }
        this.scoreboardFarmWorld = false;
    }

    public void onDisconnect() {
        this.chatDetectedFarmWorld = false;
        this.scoreboardFarmWorld = false;
        this.nonMainDimension = false;
        this.lastFarmWorldChatMs = 0L;
    }

    public boolean isInFarmWorld() {
        return this.chatDetectedFarmWorld || this.scoreboardFarmWorld || this.nonMainDimension;
    }

    public boolean shouldUseSurfaceMinimap(String mapMode, boolean isOnGermanMinerServer) {
        if ("SURFACE".equalsIgnoreCase(mapMode)) {
            return true;
        }
        if ("BLUEMAP".equalsIgnoreCase(mapMode)) {
            return false;
        }
        return isInFarmWorld() || !isOnGermanMinerServer;
    }
}
