package de.pflanzenkaktus.enhancedgm.client.render;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.map.BlueMapTextureCache;
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
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Renders the modular GM-Addon HUD widgets (BlueMap Minimap, Speed Camera Alert, and
 * 20s Spawn/Respawn Protection) plus projected 3D World Markers for nearby Speed Cameras.
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
     * Renders the BlueMap Minimap centered on the player.
     */
    public void renderMinimapWidget(GuiGraphicsExtractor graphics, Minecraft mc, float partialTick, boolean editorPreview) {
        AddonConfig cfg = addon.getConfig();
        Font font = mc.font;

        int x = cfg.minimapX;
        int y = cfg.minimapY;
        int mapSize = Math.round(cfg.minimapSize * cfg.minimapScale);
        int totalHeight = Math.round((cfg.minimapSize + 22) * cfg.minimapScale);
        int footerHeight = totalHeight - mapSize;

        // Outer card background & border (exact bounds matching hitbox)
        graphics.fill(x, y, x + mapSize, y + totalHeight, 0xCC0F172A);
        graphics.outline(x, y, mapSize, totalHeight, 0xFF334155);

        // Map viewport background
        graphics.fill(x, y, x + mapSize, y + mapSize, 0xFF0B1120);

        LocalPlayer player = mc.player;
        double px = player != null ? player.getX() : -885.0;
        double pz = player != null ? player.getZ() : -2005.0;
        float playerYaw = player != null ? player.getViewYRot(partialTick) : 0.0f;

        double visibleBlocks = Math.max(40.0, cfg.minimapZoomBlocks);
        double pixelsPerBlock = (double) mapSize / visibleBlocks;
        int centerX = x + mapSize / 2;
        int centerY = y + mapSize / 2;

        // Clip map tiles and markers inside the minimap square
        graphics.enableScissor(x, y, x + mapSize, y + mapSize);

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
                graphics.fill(mx - 2, my - 2, mx + 3, my + 3, 0xFF000000);
                graphics.fill(mx - 1, my - 1, mx + 2, my + 2, marker.colorArgb());
            }
        }

        // Draw Speed Cameras ("Blitzer") on the minimap
        if (cfg.showSpeedCamerasOnMap) {
            BlitzerWarningTracker.BlitzerAlert activeAlert = addon.getBlitzerTracker().getCurrentAlert();
            for (SpeedCamera cam : addon.getCameraRepository().getCameras()) {
                double dx = cam.x() - px;
                double dz = cam.z() - pz;
                if (Math.abs(dx) > radiusBlocks || Math.abs(dz) > radiusBlocks) continue;

                int cx = (int) Math.round(dx * pixelsPerBlock);
                int cy = (int) Math.round(dz * pixelsPerBlock);
                boolean isAlerted = activeAlert != null && activeAlert.camera().id().equals(cam.id());

                if (isAlerted && (System.currentTimeMillis() / 250L) % 2L == 0L) {
                    graphics.fill(cx - 6, cy - 6, cx + 7, cy + 7, 0x88EF4444);
                }
                graphics.fill(cx - 4, cy - 4, cx + 5, cy + 5, 0xFFDC2626);
                graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
            }
        }

        graphics.pose().popMatrix();
        graphics.disableScissor();

        // Draw Player Cursor in the exact center of the minimap
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        if (!cfg.minimapRotateWithPlayer) {
            graphics.pose().rotate((float) Math.toRadians(playerYaw + 180.0f));
        }
        graphics.fill(-3, -4, 4, 4, 0xFF0F172A);
        graphics.fill(-2, -3, 3, 3, 0xFF38BDF8);
        graphics.fill(-1, -5, 2, -2, 0xFFFFFFFF);
        graphics.pose().popMatrix();

        // Draw Cardinal North badge on the minimap border
        double northAngleRad = cfg.minimapRotateWithPlayer
            ? Math.toRadians(180.0 - playerYaw - 90.0)
            : Math.toRadians(-90.0);
        int compassRadius = Math.max(10, (mapSize / 2) - 8);
        int nx = centerX + (int) Math.round(Math.cos(northAngleRad) * compassRadius);
        int ny = centerY + (int) Math.round(Math.sin(northAngleRad) * compassRadius);
        nx = Math.clamp(nx, x + 6, x + mapSize - 6);
        ny = Math.clamp(ny, y + 6, y + mapSize - 6);
        graphics.fill(nx - 5, ny - 5, nx + 6, ny + 6, 0xCC0F172A);
        graphics.centeredText(font, "N", nx + 1, ny - 3, 0xFFF87171);

        // Footer bar: Coordinates & Speed km/h
        int footerY = y + mapSize;
        graphics.fill(x, footerY, x + mapSize, y + totalHeight, 0xFF1E293B);

        int maxFooterW = mapSize - 8;
        String coordText = String.format(Locale.ROOT, "%d, %d", (int) Math.floor(px), (int) Math.floor(pz));
        int speedKmh = (int) Math.round(addon.getBlitzerTracker().getCurrentSpeedKmh());
        String speedText = speedKmh + " km/h";

        int coordW = font.width(coordText);
        int speedW = font.width(speedText);
        int line1NeededW = coordW + speedW + 6;
        float line1Scale = 1.0f;
        if (line1NeededW > maxFooterW && maxFooterW > 10) {
            line1Scale = (float) maxFooterW / line1NeededW;
        }

        float line1Y = footerHeight >= 18 ? (footerY + 2.0f) : (footerY + Math.max(1.0f, (footerHeight - 9.0f) / 2.0f));
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 4.0f, line1Y);
        if (line1Scale < 1.0f) {
            graphics.pose().scale(line1Scale, line1Scale);
        }
        graphics.text(font, coordText, 0, 0, 0xFFE2E8F0, true);
        int speedColor = speedKmh > 70 ? 0xFFF87171 : 0xFF38BDF8;
        int speedDrawX = Math.round((maxFooterW / line1Scale) - speedW);
        graphics.text(font, speedText, speedDrawX, 0, speedColor, true);
        graphics.pose().popMatrix();

        // Footer Line 2: Responsive shortcut hints
        if (footerHeight >= 18) {
            String subLine = I18n.get("gm-addon.hud.minimap.subline");
            if (font.width(subLine) > maxFooterW) {
                subLine = I18n.get("gm-addon.hud.minimap.subline_short");
            }
            if (font.width(subLine) > maxFooterW) {
                subLine = "[M] Karte";
            }
            if (font.width(subLine) > maxFooterW) {
                subLine = "[M]";
            }

            int subW = font.width(subLine);
            float line2Scale = 1.0f;
            if (subW > maxFooterW && maxFooterW > 10) {
                line2Scale = (float) maxFooterW / subW;
            }
            if (line2Scale >= 0.60f) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(x + 4.0f, footerY + 11.0f);
                if (line2Scale < 1.0f) {
                    graphics.pose().scale(line2Scale, line2Scale);
                }
                graphics.text(font, subLine, 0, 0, 0xFF94A3B8, false);
                graphics.pose().popMatrix();
            }
        }
    }

    /**
     * Renders the Speed Camera ("Blitzer") Warning HUD Widget when approaching a camera.
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

        boolean flashing = (alert.urgency() == BlitzerWarningTracker.Urgency.OVER_SPEED_LIMIT
            || alert.urgency() == BlitzerWarningTracker.Urgency.URGENT)
            && ((System.currentTimeMillis() / 280L) % 2L == 0L);

        int borderColor = switch (alert.urgency()) {
            case OVER_SPEED_LIMIT -> flashing ? 0xFFEF4444 : 0xFF991B1B;
            case URGENT -> flashing ? 0xFFF97316 : 0xFFB45309;
            default -> 0xFFEAB308;
        };

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);

        graphics.fill(0, 0, width, height, 0xD90F172A);
        graphics.outline(0, 0, width, height, borderColor);

        int signX = 7;
        int signY = 7;
        int signSize = 32;
        graphics.fill(signX, signY, signX + signSize, signY + signSize, 0xFFDC2626);
        graphics.fill(signX + 4, signY + 4, signX + signSize - 4, signY + signSize - 4, 0xFFFFFFFF);
        String limitStr = Integer.toString(alert.speedLimitKmh());
        int limitTextX = signX + (signSize - font.width(limitStr)) / 2;
        graphics.text(font, limitStr, limitTextX, signY + 12, 0xFF0F172A, false);

        String header = alert.urgency() == BlitzerWarningTracker.Urgency.OVER_SPEED_LIMIT
            ? I18n.get("gm-addon.hud.blitzer.warning")
            : I18n.get("gm-addon.hud.blitzer.ahead");
        int headerColor = alert.urgency() == BlitzerWarningTracker.Urgency.OVER_SPEED_LIMIT
            ? 0xFFF87171
            : 0xFFFACC15;
        graphics.text(font, header, 46, 6, headerColor, true);

        String camName = alert.camera().name();
        if (font.width(camName) > 132) {
            camName = font.plainSubstrByWidth(camName, 126) + "...";
        }
        graphics.text(font, camName, 46, 17, 0xFFF8FAFC, true);

        int distMeters = (int) Math.round(alert.distanceBlocks());
        int curSpeed = (int) Math.round(alert.currentSpeedKmh());
        String infoLine = I18n.get("gm-addon.common.distance", distMeters, curSpeed);
        graphics.text(font, infoLine, 46, 29, 0xFFCBD5E1, false);

        double maxDist = Math.max(20.0, cfg.warningDistanceBlocks);
        float proximityRatio = (float) Math.clamp(1.0 - (alert.distanceBlocks() / maxDist), 0.05, 1.0);
        int barWidth = Math.round((width - 4) * proximityRatio);
        graphics.fill(2, height - 4, 2 + barWidth, height - 2, borderColor);

        graphics.pose().popMatrix();
    }

    /**
     * Renders the 20-second Spawn / Respawn Protection ("Spawnschutz") HUD Widget.
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

        int accentColor = remainingSec <= 5.0 ? 0xFFF59E0B : 0xFF10B981;

        graphics.fill(0, 0, width, height, 0xD90F172A);
        graphics.outline(0, 0, width, height, accentColor);

        graphics.fill(6, 6, 24, 24, accentColor);
        graphics.fill(8, 8, 22, 22, 0xFF0F172A);
        graphics.centeredText(font, "S", 15, 11, accentColor);

        int maxLabelW = width - 30 - font.width(timeStr) - 12;
        if (font.width(label) > maxLabelW) {
            label = font.plainSubstrByWidth(label, Math.max(10, maxLabelW - 6)) + "...";
        }

        graphics.text(font, label, 30, 6, 0xFFF8FAFC, true);
        graphics.text(font, timeStr, width - font.width(timeStr) - 6, 6, accentColor, true);

        int barX = 30;
        int barY = 20;
        int barW = width - barX - 6;
        graphics.fill(barX, barY, barX + barW, barY + 6, 0xFF1E293B);
        int fillW = Math.round(barW * progress);
        graphics.fill(barX, barY, barX + fillW, barY + 6, accentColor);

        graphics.pose().popMatrix();
    }


    /**
     * Renders the Speedometer ("Tacho") HUD Widget in either ANALOG or DIGITAL style,
     * including the active Tempomat speed and status.
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
        // Automatically auto-scale dial maximum for high-speed vehicles or aircraft so needle never clips
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
        int cy = 53;
        int radius = 40;

        boolean overLimit = hasActiveAlert && (speedKmh > speedLimit + 2);
        int borderColor = overLimit ? 0xFFEF4444 : ((tempomatOn && tempomatAvailable) ? 0xFF10B981 : 0xFF334155);

        // Instrument housing background
        graphics.fill(0, 0, width, height, 0xD90B1120);
        graphics.outline(0, 0, width, height, borderColor);

        // Inner dial face
        graphics.fill(cx - radius + 4, cy - radius + 4, cx + radius - 4, cy + radius - 4, 0x880F172A);

        // Sweep from -125 deg (bottom-left, where 0 deg = top/12 o'clock) to +125 deg (bottom-right)
        double startDeg = -125.0;
        double sweepDeg = 250.0;

        // Draw tick marks around the circular arc every 10 km/h
        int step = maxKmh > 180 ? 20 : 10;
        for (int v = 0; v <= maxKmh; v += step) {
            double ratio = (double) v / maxKmh;
            double deg = startDeg + ratio * sweepDeg;
            double rad = Math.toRadians(deg - 90.0);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);

            boolean major = (v % 40 == 0) || v == maxKmh;
            int outerX = cx + (int) Math.round(cos * radius);
            int outerY = cy + (int) Math.round(sin * radius);
            int innerX = cx + (int) Math.round(cos * (radius - (major ? 6 : 3)));
            int innerY = cy + (int) Math.round(sin * (radius - (major ? 6 : 3)));

            int tickColor;
            if (hasActiveAlert && v > speedLimit) {
                tickColor = 0xFFEF4444;
            } else if ((double) v <= speedKmh && speedKmh > 0.5) {
                tickColor = 0xFF38BDF8;
            } else {
                tickColor = major ? 0xFFE2E8F0 : 0xFF64748B;
            }

            graphics.fill(outerX - 1, outerY - 1, outerX + 2, outerY + 2, tickColor);
            if (major) {
                graphics.fill(innerX - 1, innerY - 1, innerX + 1, innerY + 1, tickColor);
                // Speed number label inside the dial
                int labelR = radius - 14;
                int lx = cx + (int) Math.round(cos * labelR);
                int ly = cy + (int) Math.round(sin * labelR);
                String numStr = Integer.toString(v);
                graphics.pose().pushMatrix();
                graphics.pose().translate((float) lx, (float) ly);
                graphics.pose().scale(0.65f, 0.65f);
                graphics.centeredText(font, numStr, 0, -4, 0xFFCBD5E1);
                graphics.pose().popMatrix();
            }
        }

        // Draw Tempomat target marker on the outer rim of the analog dial
        if (tempomatAvailable && tempomatKmh > 0) {
            double tRatio = Math.clamp((double) tempomatKmh / maxKmh, 0.0, 1.0);
            double tDeg = startDeg + tRatio * sweepDeg;
            double tRad = Math.toRadians(tDeg - 90.0);
            int tx = cx + (int) Math.round(Math.cos(tRad) * (radius + 3));
            int ty = cy + (int) Math.round(Math.sin(tRad) * (radius + 3));
            int tColor = tempomatOn ? 0xFF10B981 : 0xFF64748B;
            graphics.fill(tx - 2, ty - 2, tx + 3, ty + 3, tColor);
        }

        // Draw rotating speedometer needle
        double speedRatio = Math.clamp(speedKmh / maxKmh, 0.0, 1.0);
        float needleDeg = (float) (startDeg + speedRatio * sweepDeg);
        int needleColor = overLimit ? 0xFFEF4444 : 0xFF38BDF8;

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) cx, (float) cy);
        graphics.pose().rotate((float) Math.toRadians(needleDeg));
        // Needle shaft pointing up (0 deg = -Y)
        graphics.fill(-1, -(radius - 5), 2, 5, needleColor);
        graphics.fill(0, -(radius - 2), 1, -(radius - 5), 0xFFFFFFFF);
        graphics.pose().popMatrix();

        // Center hub cap
        graphics.fill(cx - 4, cy - 4, cx + 5, cy + 5, 0xFF1E293B);
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, needleColor);

        // Digital km/h readout inside lower dial
        int roundedSpeed = (int) Math.round(speedKmh);
        String speedStr = roundedSpeed + " km/h";
        int speedColor = overLimit ? 0xFFF87171 : 0xFFF8FAFC;
        graphics.centeredText(font, speedStr, cx, cy + 12, speedColor);

        // Bottom Tempomat status bar
        graphics.fill(4, height - 16, width - 4, height - 3, 0xFF1E293B);
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
        float maxAvailableTextW = width - 12;
        int rawTextW = font.width(tempomatText);
        float textScale = Math.min(0.8f, maxAvailableTextW / (float) Math.max(1, rawTextW));
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, height - 12);
        graphics.pose().scale(textScale, textScale);
        graphics.centeredText(font, tempomatText, 0, 0, 0xFFF8FAFC);
        graphics.pose().popMatrix();
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
        boolean overLimit = roundedSpeed > speedLimit + 2;

        int accentColor = overLimit ? 0xFFEF4444 : ((tempomatOn && tempomatAvailable) ? 0xFF10B981 : 0xFF38BDF8);

        // Card background & border
        graphics.fill(0, 0, width, height, 0xD90B1120);
        graphics.outline(0, 0, width, height, accentColor);

        // Top header bar
        graphics.fill(1, 1, width - 1, 13, 0xFF1E293B);
        graphics.text(font, I18n.get("gm-addon.hud.tacho.title"), 6, 3, 0xFFF8FAFC, false);

        String limitBadge = hasActiveAlert ? ("§cLimit " + speedLimit) : ("§7Max " + maxKmh);
        graphics.text(font, limitBadge, width - font.width(limitBadge) - 5, 3, 0xFFCBD5E1, false);

        // Large 2.0x Digital Speed Readout on the left
        String speedDigits = String.format(Locale.ROOT, "%d", roundedSpeed);
        int digitColor = overLimit ? 0xFFF87171 : 0xFFF8FAFC;
        graphics.pose().pushMatrix();
        graphics.pose().translate(8.0f, 18.0f);
        graphics.pose().scale(2.0f, 2.0f);
        graphics.text(font, speedDigits, 0, 0, digitColor, true);
        graphics.pose().popMatrix();

        int digitsWidth = font.width(speedDigits) * 2;
        graphics.text(font, "km/h", 10 + digitsWidth, 24, 0xFF94A3B8, false);

        if (fuelPercent >= 0.0f) {
            String fuelBadge = "Tank " + Math.round(fuelPercent) + "%";
            int fuelColor = fuelPercent < 15.0f ? 0xFFEF4444 : (fuelPercent < 35.0f ? 0xFFF59E0B : 0xFF38BDF8);
            graphics.pose().pushMatrix();
            graphics.pose().translate(10.0f + digitsWidth, 36.0f);
            graphics.pose().scale(0.70f, 0.70f);
            graphics.text(font, fuelBadge, 0, 0, fuelColor, false);
            graphics.pose().popMatrix();
        }

        // Tempomat status box on the right
        int boxX = 76;
        int boxY = 16;
        int boxW = width - boxX - 5;
        int boxH = 22;
        int boxBorder = (!tempomatAvailable || !tempomatOn) ? 0xFF334155 : 0xFF10B981;
        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF0F172A);
        graphics.outline(boxX, boxY, boxW, boxH, boxBorder);

        String statusHeader;
        if (!tempomatAvailable) {
            statusHeader = I18n.get("gm-addon.hud.tacho.card_no_cruise");
        } else if (tempomatOn) {
            statusHeader = I18n.get("gm-addon.hud.tacho.card_cruise_on");
        } else {
            statusHeader = I18n.get("gm-addon.hud.tacho.card_cruise_off");
        }

        int rawHeaderW = font.width(statusHeader);
        float headerScale = Math.min(0.70f, (float) (boxW - 4) / Math.max(1, rawHeaderW));
        graphics.pose().pushMatrix();
        graphics.pose().translate(boxX + boxW / 2.0f, boxY + 3.0f);
        graphics.pose().scale(headerScale, headerScale);
        graphics.centeredText(font, statusHeader, 0, 0, 0xFFF8FAFC);
        graphics.pose().popMatrix();

        String tVal = !tempomatAvailable ? "§8N/A" : (tempomatKmh + " km/h");
        int tValColor = !tempomatAvailable ? 0xFF64748B : (tempomatOn ? 0xFF10B981 : 0xFF94A3B8);
        graphics.pose().pushMatrix();
        graphics.pose().translate(boxX + boxW / 2.0f, boxY + 12.0f);
        graphics.pose().scale(0.85f, 0.85f);
        graphics.centeredText(font, tVal, 0, 0, tValColor);
        graphics.pose().popMatrix();

        // Bottom LED speed progress bar + Tempomat target notch
        int barX = 6;
        int barY = height - 11;
        int barW = width - 12;
        int barH = 6;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF1E293B);

        float fillRatio = (float) Math.clamp(speedKmh / maxKmh, 0.0, 1.0);
        int fillW = Math.round(barW * fillRatio);
        if (fillW > 0) {
            graphics.fill(barX, barY, barX + fillW, barY + barH, accentColor);
        }

        // Draw Tempomat target marker line on the progress bar
        if (tempomatAvailable && tempomatKmh > 0) {
            float tRatio = (float) Math.clamp((double) tempomatKmh / maxKmh, 0.0, 1.0);
            int tx = barX + Math.round(barW * tRatio);
            int markerColor = tempomatOn ? 0xFF10B981 : 0xFF94A3B8;
            graphics.fill(tx - 1, barY - 2, tx + 1, barY + barH + 2, markerColor);
        }
    }

    /**
     * Projects nearby Speed Cameras in 3D world space onto the HUD viewport.
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

            String badge = String.format(Locale.ROOT, "[%d] %s (%dm)", cam.effectiveSpeedLimit(), cam.name(), (int) Math.round(dist));
            int bw = font.width(badge) + 8;

            graphics.verticalLine(sx, sy + 6, sy + 18, 0xAAEF4444);
            graphics.fill(sx - bw / 2, sy - 6, sx + bw / 2, sy + 6, 0xCC0F172A);
            graphics.outline(sx - bw / 2, sy - 6, bw, 12, 0xFFEF4444);
            graphics.centeredText(font, badge, sx, sy - 4, 0xFFF8FAFC);
        }
    }
}
