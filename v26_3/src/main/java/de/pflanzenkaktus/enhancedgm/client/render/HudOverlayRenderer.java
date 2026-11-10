package de.pflanzenkaktus.enhancedgm.client.render;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.map.BlueMapTextureCache;
import de.pflanzenkaktus.enhancedgm.client.screen.GMSettingsScreen;
import de.pflanzenkaktus.enhancedgm.client.screen.HudEditorScreen;
import de.pflanzenkaktus.enhancedgm.client.util.I18n;
import de.pflanzenkaktus.enhancedgm.config.AddonConfig;
import de.pflanzenkaktus.enhancedgm.feature.BlitzerWarningTracker;
import de.pflanzenkaktus.enhancedgm.feature.SpawnProtectionTracker;
import de.pflanzenkaktus.enhancedgm.map.BlueMapTileClient;
import de.pflanzenkaktus.enhancedgm.model.BlueMapMarker;
import de.pflanzenkaktus.enhancedgm.model.SpeedCamera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

import static de.pflanzenkaktus.enhancedgm.client.screen.GMSettingsScreen.uText;

/**
 * Renders the modular GM-Addon HUD widgets in the Modern Design Language:
 * - 8x-supersampled bilinear-filtered 9-slice rounded cards (zero pixel staircases)
 * - TrueType Calibri Bold HD typography (gm-addon:modern) with adaptive auto-fitting
 * - 128x128 bilinear-filtered vector icons, circular speed signs, and analog dial rings
 */
public class HudOverlayRenderer {
    private static final SpeedCamera PREVIEW_CAMERA = new SpeedCamera(
        "preview", "Hauptbahnhof Zufahrt", -885.0, 63.0, -2005.0, 70, null, 0.0
    );
    private static final BlitzerWarningTracker.BlitzerAlert PREVIEW_ALERT = new BlitzerWarningTracker.BlitzerAlert(
        PREVIEW_CAMERA, 64.0, 74.0, 70, BlitzerWarningTracker.Urgency.OVER_SPEED_LIMIT, 0.0
    );

    private final GMAddonClient addon;

    public HudOverlayRenderer(GMAddonClient addon) {
        this.addon = addon;
    }

    public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // Do not double-render while HudEditorScreen is open (HudEditorScreen renders interactive preview)
        if (de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat.getScreen(mc) instanceof HudEditorScreen) {
            return;
        }
        if (!addon.isActiveOnCurrentServer()) {
            return;
        }

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        addon.getModuleManager().renderAllHud(graphics, mc, partialTick, false);
    }

    /**
     * Renders the BlueMap Minimap centered on the player in Modern style.
     */
    public void renderMinimapWidget(GuiGraphicsExtractor graphics, Minecraft mc, float partialTick, boolean editorPreview) {
        AddonConfig cfg = addon.getConfig();
        Font font = mc.font;

        int x = cfg.minimapX;
        int y = cfg.minimapY;
        int mapSize = Math.round(cfg.minimapSize * cfg.minimapScale);
        int footerBaseHeight = 0;
        if (cfg.minimapShowSpeedAndHints) {
            footerBaseHeight = 24;
        } else if (cfg.minimapShowCoordinates) {
            footerBaseHeight = 14;
        }
        int totalHeight = Math.round((cfg.minimapSize + footerBaseHeight) * cfg.minimapScale);
        int footerHeight = totalHeight - mapSize;

        // Single clean 8x-supersampled Modern 9-slice card frame (no double border)
        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_CARD_NORMAL, x, y, mapSize, totalHeight, 6);

        // Inset map viewport by 2px right up to the single outer border
        int pad = 2;
        int viewX = x + pad;
        int viewY = y + pad;
        int viewW = Math.max(16, mapSize - pad * 2);
        int viewH = Math.max(16, (footerHeight > 0 ? mapSize - pad : mapSize - pad * 2));

        graphics.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0xFF0B1120);

        LocalPlayer player = mc.player;
        double px = player != null ? player.getX() : -885.0;
        double py = player != null ? player.getY() : 64.0;
        double pz = player != null ? player.getZ() : -2005.0;
        float playerYaw = player != null ? player.getViewYRot(partialTick) : 0.0f;

        double visibleBlocks = Math.max(40.0, cfg.minimapZoomBlocks);
        double pixelsPerBlock = (double) viewW / visibleBlocks;
        int centerX = viewX + viewW / 2;
        int centerY = viewY + viewH / 2;

        // Clip map tiles and markers inside the minimap viewport
        graphics.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        if (cfg.minimapRotateWithPlayer) {
            float angleRad = (float) Math.toRadians(180.0 - playerYaw);
            graphics.pose().rotate(angleRad);
        }

        double radiusBlocks = (visibleBlocks * 0.75);
        int lod = 1;
        int blocksPerTile = BlueMapTileClient.blocksPerTile(lod);
        int minTileX = BlueMapTileClient.blockToTileCoord(px - radiusBlocks, lod);
        int maxTileX = BlueMapTileClient.blockToTileCoord(px + radiusBlocks, lod);
        int minTileZ = BlueMapTileClient.blockToTileCoord(pz - radiusBlocks, lod);
        int maxTileZ = BlueMapTileClient.blockToTileCoord(pz + radiusBlocks, lod);

        BlueMapTextureCache texCache = addon.getTextureCache();
        for (int tx = minTileX; tx <= maxTileX; tx++) {
            for (int tz = minTileZ; tz <= maxTileZ; tz++) {
                double tileOriginX = (double) tx * blocksPerTile;
                double tileOriginZ = (double) tz * blocksPerTile;

                int drawX = (int) Math.floor((tileOriginX - px) * pixelsPerBlock);
                int drawY = (int) Math.floor((tileOriginZ - pz) * pixelsPerBlock);
                int drawW = (int) Math.ceil(blocksPerTile * pixelsPerBlock) + 1;
                int drawH = (int) Math.ceil(blocksPerTile * pixelsPerBlock) + 1;

                BlueMapTileClient.TileKey key = new BlueMapTileClient.TileKey(lod, tx, tz);
                if (!texCache.drawTile(graphics, key, drawX, drawY, drawW, drawH)) {
                    graphics.fill(drawX, drawY, drawX + drawW, drawY + drawH, 0xFF131C31);
                }
            }
        }

        // Draw enabled BlueMap POI markers (Tankstellen, Taxipunkte, Haltestellen)
        if (cfg.showBlueMapMarkersOnMap) {
            for (BlueMapMarker marker : addon.getMarkerClient().getMarkers()) {
                if (!cfg.enabledMarkerSets.contains(marker.categoryId())) continue;
                double dx = marker.x() - px;
                double dz = marker.z() - pz;
                if (Math.abs(dx) > radiusBlocks || Math.abs(dz) > radiusBlocks) continue;

                int mx = (int) Math.round(dx * pixelsPerBlock);
                int my = (int) Math.round(dz * pixelsPerBlock);
                graphics.fill(mx - 2, my - 2, mx + 3, my + 3, 0xCC000000);
                graphics.fill(mx - 1, my - 1, mx + 2, my + 2, marker.colorArgb());
            }
        }

        // Draw Speed Cameras ("Blitzer") on the minimap using 128x128 HD circular speed sign texture
        if (cfg.showSpeedCamerasOnMap) {
            BlitzerWarningTracker.BlitzerAlert activeAlert = addon.getBlitzerTracker().getCurrentAlert();
            for (SpeedCamera cam : addon.getCameraRepository().getCameras()) {
                double dx = cam.x() - px;
                double dz = cam.z() - pz;
                if (Math.abs(dx) > radiusBlocks || Math.abs(dz) > radiusBlocks) continue;

                int cx = (int) Math.round(dx * pixelsPerBlock);
                int cy = (int) Math.round(dz * pixelsPerBlock);
                boolean isAlerted = activeAlert != null && activeAlert.camera().id().equals(cam.id());

                int iconSize = (isAlerted && (System.currentTimeMillis() / 250L) % 2L == 0L) ? 12 : 9;
                GMSettingsScreen.drawTexture(graphics, GMSettingsScreen.TEX_SPEED_SIGN, cx - iconSize / 2, cy - iconSize / 2, iconSize, iconSize);
            }
        }

        graphics.pose().popMatrix();
        graphics.disableScissor();

        // Draw Player Cursor in the exact center of the minimap using 128x128 bilinear-filtered HD textures
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);

        float markerScale = Math.max(0.2f, Math.min(3.0f, cfg.minimapPlayerMarkerScale));
        if (markerScale != 1.0f) {
            graphics.pose().scale(markerScale, markerScale);
        }

        boolean isDot = "DOT".equalsIgnoreCase(cfg.minimapPlayerMarkerShape);
        if (isDot) {
            GMSettingsScreen.drawTexture(graphics, GMSettingsScreen.TEX_PLAYER_DOT, -6, -6, 12, 12);
        } else {
            if (!cfg.minimapRotateWithPlayer) {
                graphics.pose().rotate((float) Math.toRadians(playerYaw + 180.0f));
            }
            GMSettingsScreen.drawTexture(graphics, GMSettingsScreen.TEX_PLAYER_ARROW, -7, -7, 14, 14);
        }
        graphics.pose().popMatrix();

        // Draw Cardinal North badge on the minimap border
        double northAngleRad = cfg.minimapRotateWithPlayer
            ? Math.toRadians(180.0 - playerYaw - 90.0)
            : Math.toRadians(-90.0);
        int compassRadius = Math.max(10, (viewW / 2) - 7);
        int nx = centerX + (int) Math.round(Math.cos(northAngleRad) * compassRadius);
        int ny = centerY + (int) Math.round(Math.sin(northAngleRad) * compassRadius);
        nx = Math.clamp(nx, viewX + 7, viewX + viewW - 7);
        ny = Math.clamp(ny, viewY + 7, viewY + viewH - 7);
        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_HUD_ALERT_RED, nx - 6, ny - 6, 12, 12, 4);
        GMSettingsScreen.drawCenteredFittedText(graphics, font, uText("N"), nx, ny - 4, 10, 0xFFFFFFFF, true);

        if (footerHeight > 0) {
            // Subtle single separator line between map and footer (no nested box border)
            int footerY = y + mapSize;
            graphics.fill(x + 2, footerY, x + mapSize - 2, footerY + 1, 0x3338BDF8);

            int maxFooterW = mapSize - 12;
            String coordText = cfg.minimapShowCoordinates
                ? String.format(Locale.ROOT, "%d, %d, %d", (int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz))
                : "";
            int speedKmh = (int) Math.round(addon.getBlitzerTracker().getCurrentSpeedKmh());
            String speedText = cfg.minimapShowSpeedAndHints ? (speedKmh + " km/h") : "";

            int line1Y = footerBaseHeight >= 18 ? (footerY + 3) : (footerY + Math.max(2, (footerHeight - 10) / 2));
            if (!coordText.isEmpty() && !speedText.isEmpty()) {
                Component speedComp = uText(speedText);
                int speedW = Math.min(maxFooterW / 2 - 2, font.width(speedComp));
                int speedColor = speedKmh > 70 ? 0xFFF87171 : 0xFF38BDF8;
                GMSettingsScreen.drawFittedText(graphics, font, speedComp, x + mapSize - 6 - speedW, line1Y, speedW, speedColor, true);
                GMSettingsScreen.drawFittedText(graphics, font, uText(coordText), x + 6, line1Y, maxFooterW - speedW - 6, 0xFFF1F5F9, true);
            } else if (!coordText.isEmpty()) {
                GMSettingsScreen.drawCenteredFittedText(graphics, font, uText(coordText), x + mapSize / 2, line1Y, maxFooterW, 0xFFF1F5F9, true);
            } else if (!speedText.isEmpty()) {
                int speedColor = speedKmh > 70 ? 0xFFF87171 : 0xFF38BDF8;
                GMSettingsScreen.drawCenteredFittedText(graphics, font, uText(speedText), x + mapSize / 2, line1Y, maxFooterW, speedColor, true);
            }

            // Footer Line 2: Responsive shortcut hints in Calibri Bold
            if (cfg.minimapShowSpeedAndHints && footerHeight >= 18) {
                String subLine = I18n.get("gm-addon.hud.minimap.subline");
                GMSettingsScreen.drawCenteredFittedText(graphics, font, uText(subLine), x + mapSize / 2, footerY + 13, maxFooterW, 0xFF94A3B8, false);
            }
        }
    }

    /**
     * Renders the Speed Camera ("Blitzer") Warning HUD Widget in Modern style.
     */
    public void renderBlitzerAlertWidget(GuiGraphicsExtractor graphics, Minecraft mc, boolean editorPreview) {
        BlitzerWarningTracker.BlitzerAlert alert = addon.getBlitzerTracker().getCurrentAlert();
        if (alert == null) {
            if (!editorPreview) {
                return;
            }
            alert = PREVIEW_ALERT;
        }

        AddonConfig cfg = addon.getConfig();
        Font font = mc.font;

        int x = cfg.blitzerWidgetX;
        int y = cfg.blitzerWidgetY;
        float scale = cfg.blitzerWidgetScale;
        int width = 186;
        int height = 46;

        boolean isOverLimit = alert.urgency() == BlitzerWarningTracker.Urgency.OVER_SPEED_LIMIT;
        boolean isUrgent = alert.urgency() == BlitzerWarningTracker.Urgency.URGENT;
        boolean flashing = (isOverLimit || isUrgent) && ((System.currentTimeMillis() / 280L) % 2L == 0L);

        Identifier cardTex = (isOverLimit || (isUrgent && flashing))
            ? GMSettingsScreen.TEX_HUD_ALERT_RED
            : GMSettingsScreen.TEX_HUD_ALERT_AMBER;
        Identifier barTex = isOverLimit ? GMSettingsScreen.TEX_BAR_FILL_RED : GMSettingsScreen.TEX_BAR_FILL_AMBER;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);

        // Smooth 8x-supersampled 9-slice alert card
        GMSettingsScreen.drawNineSlice(graphics, cardTex, 0, 0, width, height, 8);

        // Circular 128x128 HD European Speed Limit Sign
        int signX = 7;
        int signY = 6;
        int signSize = 30;
        GMSettingsScreen.drawTexture(graphics, GMSettingsScreen.TEX_SPEED_SIGN, signX, signY, signSize, signSize);
        Component limitComp = uText(Integer.toString(alert.speedLimitKmh()));
        GMSettingsScreen.drawCenteredFittedText(graphics, font, limitComp, signX + signSize / 2, signY + (signSize - 8) / 2, signSize - 10, 0xFF0F172A, false);

        int textX = 43;
        int maxTextW = width - textX - 7;

        String header = isOverLimit
            ? I18n.get("gm-addon.hud.blitzer.warning")
            : I18n.get("gm-addon.hud.blitzer.ahead");
        int headerColor = isOverLimit ? 0xFFFCA5A5 : 0xFFFDE047;
        GMSettingsScreen.drawFittedText(graphics, font, uText(header), textX, 6, maxTextW, headerColor, true);

        GMSettingsScreen.drawFittedText(graphics, font, uText(alert.camera().name()), textX, 16, maxTextW, 0xFFFFFFFF, true);

        int distMeters = (int) Math.round(alert.distanceBlocks());
        int curSpeed = (int) Math.round(alert.currentSpeedKmh());
        String infoLine = I18n.get("gm-addon.common.distance", distMeters, curSpeed);
        GMSettingsScreen.drawFittedText(graphics, font, uText(infoLine), textX, 27, maxTextW, 0xFFCBD5E1, false);

        // Smooth rounded proximity progress bar at the bottom
        double maxDist = Math.max(20.0, cfg.warningDistanceBlocks);
        float proximityRatio = (float) Math.clamp(1.0 - (alert.distanceBlocks() / maxDist), 0.05, 1.0);
        int barTrackW = width - 12;
        int barFillW = Math.max(8, Math.round(barTrackW * proximityRatio));
        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_SLIDER_TRACK, 6, height - 7, barTrackW, 4, 2);
        GMSettingsScreen.drawNineSlice(graphics, barTex, 6, height - 7, barFillW, 4, 2);

        graphics.pose().popMatrix();
    }

    /**
     * Renders the 20-second Spawn / Respawn Protection ("Spawnschutz") HUD Widget in Modern style.
     */
    public void renderSpawnProtectionWidget(GuiGraphicsExtractor graphics, Minecraft mc, boolean editorPreview) {
        SpawnProtectionTracker tracker = addon.getSpawnProtectionTracker();
        boolean active = tracker.isActive();
        if (!active && !editorPreview) {
            return;
        }

        AddonConfig cfg = addon.getConfig();
        Font font = mc.font;

        double remainingSec = active ? tracker.getRemainingSeconds() : cfg.spawnProtectionSeconds * 0.72;
        float progress = active ? tracker.getProgressRatio() : 0.72f;
        String reason = tracker.getTriggerReason();
        String label;
        if (!active) {
            label = I18n.get("gm-addon.hud.spawn_protection.preview");
        } else if ("Login-Spawnschutz".equalsIgnoreCase(reason)) {
            label = I18n.get("gm-addon.hud.spawn_protection.login");
        } else if ("Respawn-Schutz".equalsIgnoreCase(reason)) {
            label = I18n.get("gm-addon.hud.spawn_protection.respawn");
        } else if ("Krankenhaus-Schutz".equalsIgnoreCase(reason)) {
            label = I18n.get("gm-addon.hud.spawn_protection.hospital");
        } else {
            label = I18n.get("gm-addon.hud.spawn_protection.generic");
        }
        String timeStr = String.format(Locale.ROOT, "%.1fs", remainingSec);

        int width = 210;
        int height = 34;

        int x = cfg.spawnProtectionX;
        int y = cfg.spawnProtectionY;
        float scale = cfg.spawnProtectionScale;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);

        boolean warningSoon = remainingSec <= 5.0;
        Identifier cardTex = warningSoon ? GMSettingsScreen.TEX_HUD_ALERT_AMBER : GMSettingsScreen.TEX_HUD_ALERT_GREEN;
        Identifier barTex = warningSoon ? GMSettingsScreen.TEX_BAR_FILL_AMBER : GMSettingsScreen.TEX_BAR_FILL_GREEN;
        int accentColor = warningSoon ? 0xFFFBBF24 : 0xFF34D399;

        // Smooth 8x-supersampled 9-slice card
        GMSettingsScreen.drawNineSlice(graphics, cardTex, 0, 0, width, height, 8);

        // Left 22x22 rounded icon badge with 128x128 HD Shield Icon
        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_CONTAINER_BG, 6, 6, 22, 22, 6);
        GMSettingsScreen.drawTexture(graphics, GMSettingsScreen.ICON_PROTECTION, 9, 9, 16, 16);

        Component timeComp = uText(timeStr);
        int timeW = font.width(timeComp);
        graphics.text(font, timeComp, width - timeW - 7, 7, accentColor, true);

        int maxLabelW = width - 34 - timeW - 12;
        GMSettingsScreen.drawFittedText(graphics, font, uText(label), 33, 7, maxLabelW, 0xFFFFFFFF, true);

        // Smooth rounded progress bar
        int barX = 33;
        int barY = 20;
        int barW = width - barX - 7;
        int barH = 6;
        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_SLIDER_TRACK, barX, barY, barW, barH, 3);
        int fillW = Math.round(barW * Math.clamp(progress, 0.0f, 1.0f));
        if (fillW > 2) {
            GMSettingsScreen.drawNineSlice(graphics, barTex, barX, barY, Math.max(8, fillW), barH, 3);
        }

        graphics.pose().popMatrix();
    }

    /**
     * Renders the Speedometer ("Tacho") HUD Widget in either ANALOG or DIGITAL Modern style.
     */
    public void renderTachoWidget(GuiGraphicsExtractor graphics, Minecraft mc, float partialTick, boolean editorPreview) {
        AddonConfig cfg = addon.getConfig();
        BlitzerWarningTracker tracker = addon.getBlitzerTracker();

        boolean inVehicle = GMAddonClient.isPlayerInActualVehicle(mc.player);
        if (!editorPreview && cfg.tachoOnlyInVehicle && !inVehicle) {
            return;
        }

        double speedKmh = tracker.getCurrentSpeedKmh();
        boolean tempomatOn = tracker.isTempomatActive();
        int tempomatKmh = tracker.getTempomatSpeedKmh();
        boolean tempomatAvailable = tracker.isTempomatAvailable();

        if (editorPreview && speedKmh < 1.0) {
            speedKmh = 50.0;
            tempomatOn = true;
            tempomatKmh = 50;
            tempomatAvailable = true;
        }

        BlitzerWarningTracker.BlitzerAlert activeAlert = tracker.getCurrentAlert();
        int speedLimit = activeAlert != null ? activeAlert.speedLimitKmh() : 70;
        int configMax = Math.max(80, cfg.tachoMaxKmh);
        int maxKmh = Math.max(configMax, (int) Math.ceil((speedKmh + 5.0) / 20.0) * 20);
        if (maxKmh < 160) maxKmh = 160;

        boolean isAircraft = tracker.isAircraft();
        boolean hasActiveAlert = (activeAlert != null && !isAircraft);

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) cfg.tachoX, (float) cfg.tachoY);
        graphics.pose().scale(cfg.tachoScale, cfg.tachoScale);
        float fuelPercent = tracker.getFuelPercent();

        if ("DIGITAL".equalsIgnoreCase(cfg.tachoStyle)) {
            renderDigitalTacho(graphics, mc.font, speedKmh, maxKmh, tempomatOn, tempomatKmh, tempomatAvailable, speedLimit, hasActiveAlert, fuelPercent);
        } else {
            renderAnalogTacho(graphics, mc.font, speedKmh, maxKmh, tempomatOn, tempomatKmh, tempomatAvailable, speedLimit, hasActiveAlert, fuelPercent);
        }

        graphics.pose().popMatrix();
    }

    private void renderAnalogTacho(
        GuiGraphicsExtractor graphics,
        Font font,
        double speedKmh,
        int maxKmh,
        boolean tempomatOn,
        int tempomatKmh,
        boolean tempomatAvailable,
        int speedLimit,
        boolean hasActiveAlert,
        float fuelPercent
    ) {
        int width = 112;
        int height = 112;
        int cx = 56;
        int cy = 49;
        int radius = 38;

        boolean overLimit = hasActiveAlert && (speedKmh > speedLimit + 2);
        Identifier housingTex = overLimit
            ? GMSettingsScreen.TEX_HUD_ALERT_RED
            : ((tempomatOn && tempomatAvailable) ? GMSettingsScreen.TEX_HUD_ALERT_GREEN : GMSettingsScreen.TEX_WINDOW_BG);

        // Smooth 8x-supersampled 9-slice instrument housing
        GMSettingsScreen.drawNineSlice(graphics, housingTex, 0, 0, width, height, 8);

        // 256x256 Bilinear-Filtered Circular Dial Face
        GMSettingsScreen.drawTexture(graphics, GMSettingsScreen.TEX_TACHO_DIAL, cx - 43, cy - 43, 86, 86);

        double startDeg = -125.0;
        double sweepDeg = 250.0;

        int step = maxKmh > 180 ? 20 : 10;
        for (int v = 0; v <= maxKmh; v += step) {
            double ratio = (double) v / maxKmh;
            double deg = startDeg + ratio * sweepDeg;
            double rad = Math.toRadians(deg - 90.0);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);

            boolean major = (v % 40 == 0) || v == maxKmh;
            int outerX = cx + (int) Math.round(cos * (radius - 2));
            int outerY = cy + (int) Math.round(sin * (radius - 2));

            int tickColor;
            if (hasActiveAlert && v > speedLimit) {
                tickColor = 0xFFEF4444;
            } else if ((double) v <= speedKmh && speedKmh > 0.5) {
                tickColor = 0xFF38BDF8;
            } else {
                tickColor = major ? 0xFFF1F5F9 : 0xFF64748B;
            }

            graphics.fill(outerX - 1, outerY - 1, outerX + 1, outerY + 1, tickColor);
            if (major) {
                int labelR = radius - 11;
                int lx = cx + (int) Math.round(cos * labelR);
                int ly = cy + (int) Math.round(sin * labelR);
                Component numComp = uText(Integer.toString(v));
                int nw = font.width(numComp);
                graphics.pose().pushMatrix();
                graphics.pose().translate((float) lx - (nw * 0.60f) * 0.5f, (float) ly - 2.5f);
                graphics.pose().scale(0.60f, 0.60f);
                graphics.text(font, numComp, 0, 0, 0xFFCBD5E1, false);
                graphics.pose().popMatrix();
            }
        }

        // Tempomat target marker on outer rim using HD GPS dot texture
        if (tempomatAvailable && tempomatKmh > 0) {
            double tRatio = Math.clamp((double) tempomatKmh / maxKmh, 0.0, 1.0);
            double tDeg = startDeg + tRatio * sweepDeg;
            double tRad = Math.toRadians(tDeg - 90.0);
            int tx = cx + (int) Math.round(Math.cos(tRad) * (radius + 1));
            int ty = cy + (int) Math.round(Math.sin(tRad) * (radius + 1));
            GMSettingsScreen.drawTexture(graphics, GMSettingsScreen.TEX_PLAYER_DOT, tx - 4, ty - 4, 8, 8);
        }

        // Rotating speedometer needle
        double speedRatio = Math.clamp(speedKmh / maxKmh, 0.0, 1.0);
        float needleDeg = (float) (startDeg + speedRatio * sweepDeg);
        int needleColor = overLimit ? 0xFFEF4444 : 0xFF38BDF8;

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) cx, (float) cy);
        graphics.pose().rotate((float) Math.toRadians(needleDeg));
        graphics.fill(-1, -(radius - 6), 1, 4, needleColor);
        graphics.fill(-1, -(radius - 3), 1, -(radius - 6), 0xFFFFFFFF);
        graphics.pose().popMatrix();

        // Smooth Circular Center Hub Cap with Gaussian Shadow
        GMSettingsScreen.drawTexture(graphics, GMSettingsScreen.TEX_SWITCH_KNOB, cx - 5, cy - 5, 10, 10);

        // Digital km/h readout inside lower dial in Calibri Bold
        int roundedSpeed = (int) Math.round(speedKmh);
        Component speedComp = uText(roundedSpeed + " km/h");
        int speedColor = overLimit ? 0xFFFCA5A5 : 0xFFFFFFFF;
        GMSettingsScreen.drawCenteredFittedText(graphics, font, speedComp, cx, cy + 13, 60, speedColor, true);

        // Bottom Tempomat & Fuel rounded status pill
        Identifier pillTex = (tempomatAvailable && tempomatOn) ? GMSettingsScreen.TEX_CAT_ACTIVE : GMSettingsScreen.TEX_CONTAINER_BG;
        GMSettingsScreen.drawNineSlice(graphics, pillTex, 5, height - 19, width - 10, 15, 5);

        String tempomatText;
        if (!tempomatAvailable) {
            tempomatText = I18n.get("gm-addon.hud.tacho.no_cruise");
        } else if (tempomatOn) {
            tempomatText = I18n.get("gm-addon.hud.tacho.cruise_on", tempomatKmh);
        } else {
            tempomatText = I18n.get("gm-addon.hud.tacho.cruise_off", tempomatKmh);
        }
        if (fuelPercent >= 0.0f) {
            tempomatText += " | " + Math.round(fuelPercent) + "%";
        }
        int statusColor = !tempomatAvailable ? 0xFF94A3B8 : (tempomatOn ? 0xFFFFFFFF : 0xFFE2E8F0);
        GMSettingsScreen.drawCenteredFittedText(graphics, font, uText(tempomatText), cx, height - 15, width - 18, statusColor, true);
    }

    private void renderDigitalTacho(
        GuiGraphicsExtractor graphics,
        Font font,
        double speedKmh,
        int maxKmh,
        boolean tempomatOn,
        int tempomatKmh,
        boolean tempomatAvailable,
        int speedLimit,
        boolean hasActiveAlert,
        float fuelPercent
    ) {
        int width = 136;
        int height = 62;
        int roundedSpeed = (int) Math.round(speedKmh);
        boolean overLimit = hasActiveAlert && (roundedSpeed > speedLimit + 2);

        Identifier cardTex = overLimit
            ? GMSettingsScreen.TEX_HUD_ALERT_RED
            : ((tempomatOn && tempomatAvailable) ? GMSettingsScreen.TEX_HUD_ALERT_GREEN : GMSettingsScreen.TEX_WINDOW_BG);

        // Smooth 8x-supersampled 9-slice card background
        GMSettingsScreen.drawNineSlice(graphics, cardTex, 0, 0, width, height, 8);

        // Top header bar
        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_CONTAINER_BG, 4, 4, width - 8, 13, 5);
        GMSettingsScreen.drawTexture(graphics, GMSettingsScreen.ICON_TACHO, 7, 5, 10, 10);
        GMSettingsScreen.drawFittedText(graphics, font, uText(I18n.get("gm-addon.hud.tacho.title")), 20, 7, 56, 0xFF38BDF8, true);

        String limitBadge = hasActiveAlert ? ("Limit " + speedLimit) : ("Max " + maxKmh);
        int limitColor = hasActiveAlert ? 0xFFFCA5A5 : 0xFF94A3B8;
        Component limitComp = uText(limitBadge);
        int limitW = font.width(limitComp);
        GMSettingsScreen.drawFittedText(graphics, font, limitComp, width - limitW - 8, 7, 56, limitColor, false);

        // Large Digital Speed Readout on the left in Calibri Bold
        Component speedDigitsComp = uText(String.format(Locale.ROOT, "%d", roundedSpeed));
        int digitColor = overLimit ? 0xFFFCA5A5 : 0xFFFFFFFF;
        int rawDigitW = font.width(speedDigitsComp);
        float digitScale = (rawDigitW * 1.85f > 44.0f) ? (44.0f / Math.max(1.0f, rawDigitW)) : 1.85f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(8.0f, 21.0f);
        graphics.pose().scale(digitScale, digitScale);
        graphics.text(font, speedDigitsComp, 0, 0, digitColor, true);
        graphics.pose().popMatrix();

        int digitsWidth = Math.round(rawDigitW * digitScale);
        int unitX = 11 + digitsWidth;
        int maxUnitW = Math.max(18, 72 - unitX);
        GMSettingsScreen.drawFittedText(graphics, font, uText("km/h"), unitX, 22, maxUnitW, 0xFF94A3B8, false);

        if (fuelPercent >= 0.0f) {
            String fuelBadge = "Tank " + Math.round(fuelPercent) + "%";
            int fuelColor = fuelPercent < 15.0f ? 0xFFEF4444 : (fuelPercent < 35.0f ? 0xFFF59E0B : 0xFF38BDF8);
            GMSettingsScreen.drawFittedText(graphics, font, uText(fuelBadge), unitX, 32, maxUnitW, fuelColor, false);
        }

        // Tempomat rounded status pill on the right
        int boxX = 74;
        int boxY = 19;
        int boxW = width - boxX - 5;
        int boxH = 26;
        Identifier boxTex = (!tempomatAvailable || !tempomatOn) ? GMSettingsScreen.TEX_CONTAINER_BG : GMSettingsScreen.TEX_HUD_ALERT_GREEN;
        GMSettingsScreen.drawNineSlice(graphics, boxTex, boxX, boxY, boxW, boxH, 6);

        String statusHeader;
        if (!tempomatAvailable) {
            statusHeader = I18n.get("gm-addon.hud.tacho.card_no_cruise");
        } else if (tempomatOn) {
            statusHeader = I18n.get("gm-addon.hud.tacho.card_cruise_on");
        } else {
            statusHeader = I18n.get("gm-addon.hud.tacho.card_cruise_off");
        }
        int headerColor = !tempomatAvailable ? 0xFF64748B : (tempomatOn ? 0xFF34D399 : 0xFFCBD5E1);
        GMSettingsScreen.drawCenteredFittedText(graphics, font, uText(statusHeader), boxX + boxW / 2, boxY + 4, boxW - 8, headerColor, true);

        String tVal = !tempomatAvailable ? "N/A" : (tempomatKmh + " km/h");
        int tValColor = !tempomatAvailable ? 0xFF64748B : (tempomatOn ? 0xFFFFFFFF : 0xFF94A3B8);
        GMSettingsScreen.drawCenteredFittedText(graphics, font, uText(tVal), boxX + boxW / 2, boxY + 14, boxW - 8, tValColor, true);

        // Smooth rounded speed progress bar at the bottom
        int barX = 6;
        int barY = height - 11;
        int barW = width - 12;
        int barH = 6;
        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_SLIDER_TRACK, barX, barY, barW, barH, 3);

        float fillRatio = (float) Math.clamp(speedKmh / maxKmh, 0.0, 1.0);
        int fillW = Math.round(barW * fillRatio);
        if (fillW > 2) {
            Identifier barFillTex = overLimit
                ? GMSettingsScreen.TEX_BAR_FILL_RED
                : ((tempomatOn && tempomatAvailable) ? GMSettingsScreen.TEX_BAR_FILL_GREEN : GMSettingsScreen.TEX_SLIDER_FILL);
            GMSettingsScreen.drawNineSlice(graphics, barFillTex, barX, barY, Math.max(8, fillW), barH, 3);
        }

        // Tempomat target indicator dot on progress bar
        if (tempomatAvailable && tempomatKmh > 0) {
            float tRatio = (float) Math.clamp((double) tempomatKmh / maxKmh, 0.0, 1.0);
            int tx = barX + Math.round(barW * tRatio);
            GMSettingsScreen.drawTexture(graphics, GMSettingsScreen.TEX_SWITCH_KNOB, tx - 4, barY - 1, 8, 8);
        }
    }

    /**
     * Projects nearby Speed Cameras in 3D world space onto the HUD viewport in Modern style.
     */
    public void render3dWorldBlitzerMarkers(GuiGraphicsExtractor graphics, Minecraft mc, float partialTick) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        AddonConfig cfg = addon.getConfig();
        Vec3 eyePos = player.getEyePosition(partialTick);
        float yaw = player.getViewYRot(partialTick);
        float pitch = player.getViewXRot(partialTick);
        double fovDeg = mc.options.fov().get();

        int guiW = graphics.guiWidth();
        int guiH = graphics.guiHeight();
        Font font = mc.font;

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);

        double fx = -sinYaw * cosPitch;
        double fy = -sinPitch;
        double fz = cosYaw * cosPitch;

        double rx = -cosYaw;
        double ry = 0.0;
        double rz = -sinYaw;

        double ux = -sinYaw * sinPitch;
        double uy = cosPitch;
        double uz = cosYaw * sinPitch;

        double tanHalfFov = Math.tan(Math.toRadians(fovDeg * 0.5));
        double aspect = (double) guiW / Math.max(1.0, (double) guiH);

        List<SpeedCamera> cameras = addon.getCameraRepository().getCameras();
        for (SpeedCamera cam : cameras) {
            double dist = cam.distance3dTo(eyePos.x, eyePos.y, eyePos.z);
            double maxRange = cam.triggerRadius() > 0 ? cam.triggerRadius() : cfg.warningDistanceBlocks;
            if (dist > maxRange || dist < 1.5) continue;

            double dx = (cam.x() + 0.5) - eyePos.x;
            double dy = (cam.y() + 1.8) - eyePos.y;
            double dz = (cam.z() + 0.5) - eyePos.z;

            double zCam = dx * fx + dy * fy + dz * fz;
            if (zCam <= 0.2) continue;

            double xCam = dx * rx + dy * ry + dz * rz;
            double yCam = dx * ux + dy * uy + dz * uz;

            double ndcX = xCam / (zCam * tanHalfFov * aspect);
            double ndcY = yCam / (zCam * tanHalfFov);
            if (Math.abs(ndcX) > 1.15 || Math.abs(ndcY) > 1.15) continue;

            int sx = (int) Math.round((guiW * 0.5) * (1.0 + ndcX));
            int sy = (int) Math.round((guiH * 0.5) * (1.0 - ndcY));

            Component badgeComp = uText(String.format(Locale.ROOT, "%d km/h • %s (%dm)", cam.effectiveSpeedLimit(), cam.name(), (int) Math.round(dist)));
            int textW = font.width(badgeComp);
            int bw = textW + 26;
            int bh = 18;
            int bx = sx - bw / 2;
            int by = sy - 9;

            graphics.verticalLine(sx, by + bh, by + bh + 10, 0xCCEF4444);
            GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_HUD_ALERT_RED, bx, by, bw, bh, 6);
            GMSettingsScreen.drawTexture(graphics, GMSettingsScreen.TEX_SPEED_SIGN, bx + 4, by + 3, 12, 12);
            graphics.text(font, badgeComp, bx + 20, by + 5, 0xFFFFFFFF, true);
        }
    }
}
