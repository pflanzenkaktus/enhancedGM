package de.pflanzenkaktus.enhancedgm.client.screen;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.map.BlueMapTextureCache;
import de.pflanzenkaktus.enhancedgm.client.util.I18n;
import de.pflanzenkaktus.enhancedgm.config.AddonConfig;
import de.pflanzenkaktus.enhancedgm.map.BlueMapTileClient;
import de.pflanzenkaktus.enhancedgm.model.BlueMapMarker;
import de.pflanzenkaktus.enhancedgm.model.SpeedCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Interactive Fullscreen GermanMiner BlueMap screen inside Minecraft in Modern style.
 * Supports drag-to-pan, scroll-to-zoom (LOD 1 & LOD 2), Follow-Player lock,
 * Speed Camera & BlueMap POI marker filtering, and hover tooltips.
 */
public class FullscreenMapScreen extends Screen {
    private static final int TOP_BAR_HEIGHT = 36;
    private static final int BOTTOM_BAR_HEIGHT = 28;

    private final GMAddonClient addon;
    private final Screen parentScreen;

    private GMSettingsScreen.ModernGlassButton followButton;
    private double centerWorldX = -885.0;
    private double centerWorldZ = -2005.0;
    private double pixelsPerBlock = 0.85;
    private boolean followPlayer = true;
    private boolean draggingMap = false;
    private double lastMouseX = 0.0;
    private double lastMouseY = 0.0;

    public FullscreenMapScreen(GMAddonClient addon, Screen parentScreen) {
        super(GMSettingsScreen.uText("GermanMiner BlueMap"));
        this.addon = addon;
        this.parentScreen = parentScreen;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            this.centerWorldX = mc.player.getX();
            this.centerWorldZ = mc.player.getZ();
        }
    }

    @Override
    protected void init() {
        AddonConfig cfg = addon.getConfig();
        int bx = 10;
        int by = 8;
        int bh = 20;
        int gap = 4;

        // Calculate available width for left filter buttons so they never overlap right navigation buttons
        int rightNavWidth = 222;
        int availLeftW = Math.max(320, this.width - rightNavWidth - 16);
        int wFollow = Math.min(86, availLeftW / 6 - gap);
        int wCam = Math.min(84, availLeftW / 6 - gap);
        int wGas = Math.min(76, availLeftW / 6 - gap);
        int wTaxi = Math.min(72, availLeftW / 6 - gap);
        int wTransit = Math.min(76, availLeftW / 6 - gap);
        int wReload = Math.min(76, availLeftW / 6 - gap);

        // 1. Follow Player toggle
        followButton = new GMSettingsScreen.ModernGlassButton(
            bx, by, wFollow, bh,
            getFollowButtonText(),
            GMSettingsScreen.getSettingIcon("marker"),
            btn -> {
                followPlayer = !followPlayer;
                if (followPlayer && Minecraft.getInstance().player != null) {
                    centerWorldX = Minecraft.getInstance().player.getX();
                    centerWorldZ = Minecraft.getInstance().player.getZ();
                }
                followButton.setMessage(getFollowButtonText());
                followButton.setAccent(followPlayer);
            },
            followPlayer
        );
        addRenderableWidget(followButton);
        bx += wFollow + gap;

        // 2. Speed Cameras toggle
        GMSettingsScreen.ModernGlassButton camBtn = new GMSettingsScreen.ModernGlassButton(
            bx, by, wCam, bh,
            getBlitzerButtonText(cfg),
            GMSettingsScreen.getCategoryIcon("blitzer"),
            btn -> {
                cfg.showSpeedCamerasOnMap = !cfg.showSpeedCamerasOnMap;
                addon.saveConfig();
                btn.setMessage(getBlitzerButtonText(cfg));
                ((GMSettingsScreen.ModernGlassButton) btn).setAccent(cfg.showSpeedCamerasOnMap);
            },
            cfg.showSpeedCamerasOnMap
        );
        addRenderableWidget(camBtn);
        bx += wCam + gap;

        // 3. Tankstellen toggle
        boolean gasOn = cfg.enabledMarkerSets.contains("Tankstellen");
        addRenderableWidget(new GMSettingsScreen.ModernGlassButton(
            bx, by, wGas, bh,
            getCategoryText(cfg, "Tankstellen", I18n.get("gm-addon.screen.map.gas")),
            btn -> {
                toggleCategory(cfg, "Tankstellen");
                btn.setMessage(getCategoryText(cfg, "Tankstellen", I18n.get("gm-addon.screen.map.gas")));
                ((GMSettingsScreen.ModernGlassButton) btn).setAccent(cfg.enabledMarkerSets.contains("Tankstellen"));
            },
            gasOn
        ));
        bx += wGas + gap;

        // 4. Taxipunkte toggle
        boolean taxiOn = cfg.enabledMarkerSets.contains("Taxipunkte");
        addRenderableWidget(new GMSettingsScreen.ModernGlassButton(
            bx, by, wTaxi, bh,
            getCategoryText(cfg, "Taxipunkte", I18n.get("gm-addon.screen.map.taxis")),
            btn -> {
                toggleCategory(cfg, "Taxipunkte");
                btn.setMessage(getCategoryText(cfg, "Taxipunkte", I18n.get("gm-addon.screen.map.taxis")));
                ((GMSettingsScreen.ModernGlassButton) btn).setAccent(cfg.enabledMarkerSets.contains("Taxipunkte"));
            },
            taxiOn
        ));
        bx += wTaxi + gap;

        // 5. Haltestellen (Bus / S-Bahn / Kabelwagen) toggle
        boolean transitOn = cfg.enabledMarkerSets.contains("Bushaltestellen");
        addRenderableWidget(new GMSettingsScreen.ModernGlassButton(
            bx, by, wTransit, bh,
            getTransitText(cfg),
            btn -> {
                boolean enabled = cfg.enabledMarkerSets.contains("Bushaltestellen");
                setTransitEnabled(cfg, !enabled);
                btn.setMessage(getTransitText(cfg));
                ((GMSettingsScreen.ModernGlassButton) btn).setAccent(!enabled);
            },
            transitOn
        ));
        bx += wTransit + gap;

        // 6. Refresh Tiles & Markers button
        addRenderableWidget(new GMSettingsScreen.ModernGlassButton(
            bx, by, wReload, bh,
            GMSettingsScreen.uText(I18n.get("gm-addon.common.reload")),
            GMSettingsScreen.ICON_RESET,
            btn -> {
                addon.getTextureCache().clearGpuCache();
                addon.getMarkerClient().refreshAsync();
                addon.getCameraRepository().syncWithFirebaseAsync();
            },
            false
        ));

        // Right-aligned navigation buttons: Settings, HUD Editor, Close
        int closeX = this.width - 32;
        int hudX = closeX - 84 - gap;
        int settingsX = hudX - 96 - gap;

        addRenderableWidget(new GMSettingsScreen.ModernGlassButton(
            settingsX, by, 96, bh,
            GMSettingsScreen.uText(I18n.get("gm-addon.common.settings")),
            GMSettingsScreen.getCategoryIcon("general"),
            btn -> de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat.setScreen(Minecraft.getInstance(), new GMSettingsScreen(addon, this)),
            false
        ));

        addRenderableWidget(new GMSettingsScreen.ModernGlassButton(
            hudX, by, 84, bh,
            GMSettingsScreen.uText(I18n.get("gm-addon.screen.map.hud_editor")),
            GMSettingsScreen.ICON_HUD,
            btn -> de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat.setScreen(Minecraft.getInstance(), new HudEditorScreen(addon, this)),
            false
        ));

        addRenderableWidget(new GMSettingsScreen.CloseButton(
            closeX, by, 22, bh,
            btn -> onClose()
        ));
    }

    private Component getFollowButtonText() {
        return GMSettingsScreen.uText(followPlayer ? I18n.get("gm-addon.screen.map.follow_on") : I18n.get("gm-addon.screen.map.follow_off"));
    }

    private Component getBlitzerButtonText(AddonConfig cfg) {
        return GMSettingsScreen.uText(I18n.get("gm-addon.screen.map.cameras", cfg.showSpeedCamerasOnMap ? I18n.get("gm-addon.common.on") : I18n.get("gm-addon.common.off")));
    }

    private Component getCategoryText(AddonConfig cfg, String id, String label) {
        boolean on = cfg.enabledMarkerSets.contains(id);
        return GMSettingsScreen.uText(label + ": " + (on ? I18n.get("gm-addon.common.on") : I18n.get("gm-addon.common.off")));
    }

    private Component getTransitText(AddonConfig cfg) {
        boolean on = cfg.enabledMarkerSets.contains("Bushaltestellen");
        return GMSettingsScreen.uText(I18n.get("gm-addon.screen.map.transit", on ? I18n.get("gm-addon.common.on") : I18n.get("gm-addon.common.off")));
    }

    private void toggleCategory(AddonConfig cfg, String id) {
        if (cfg.enabledMarkerSets.contains(id)) {
            cfg.enabledMarkerSets.remove(id);
        } else {
            cfg.enabledMarkerSets.add(id);
        }
        addon.saveConfig();
    }

    private void setTransitEnabled(AddonConfig cfg, boolean enable) {
        List<String> transit = List.of("Bushaltestellen", "S-Bahnhaltestellen", "Kabelwagenhaltestellen");
        cfg.enabledMarkerSets.removeAll(transit);
        if (enable) {
            cfg.enabledMarkerSets.addAll(transit);
        }
        addon.saveConfig();
    }

    private void disableFollowPlayer() {
        if (followPlayer) {
            followPlayer = false;
            if (followButton != null) {
                followButton.setMessage(getFollowButtonText());
                followButton.setAccent(false);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Track drag delta smoothly on every frame as fallback
        if (draggingMap) {
            double dx = mouseX - lastMouseX;
            double dy = mouseY - lastMouseY;
            if (Math.abs(dx) > 0.01 || Math.abs(dy) > 0.01) {
                disableFollowPlayer();
                centerWorldX -= dx / pixelsPerBlock;
                centerWorldZ -= dy / pixelsPerBlock;
            }
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        Minecraft mc = Minecraft.getInstance();
        if (followPlayer && mc.player != null) {
            centerWorldX = mc.player.getX();
            centerWorldZ = mc.player.getZ();
        }

        int mapTop = TOP_BAR_HEIGHT;
        int mapBottom = this.height - BOTTOM_BAR_HEIGHT;
        int mapWidth = this.width;
        int mapHeight = Math.max(20, mapBottom - mapTop);
        int screenCenterX = mapWidth / 2;
        int screenCenterY = mapTop + mapHeight / 2;

        graphics.fill(0, 0, this.width, this.height, 0xFF090D16);
        graphics.enableScissor(0, mapTop, this.width, mapBottom);

        int lod = pixelsPerBlock < 0.18 ? 2 : 1;
        int blocksPerTile = BlueMapTileClient.blocksPerTile(lod);

        double halfBlocksW = (mapWidth * 0.5) / pixelsPerBlock;
        double halfBlocksH = (mapHeight * 0.5) / pixelsPerBlock;

        int minTileX = BlueMapTileClient.blockToTileCoord(centerWorldX - halfBlocksW, lod);
        int maxTileX = BlueMapTileClient.blockToTileCoord(centerWorldX + halfBlocksW, lod);
        int minTileZ = BlueMapTileClient.blockToTileCoord(centerWorldZ - halfBlocksH, lod);
        int maxTileZ = BlueMapTileClient.blockToTileCoord(centerWorldZ + halfBlocksH, lod);

        BlueMapTextureCache texCache = addon.getTextureCache();
        for (int tx = minTileX; tx <= maxTileX; tx++) {
            for (int tz = minTileZ; tz <= maxTileZ; tz++) {
                double tileOriginX = (double) tx * blocksPerTile;
                double tileOriginZ = (double) tz * blocksPerTile;

                int drawX = screenCenterX + (int) Math.floor((tileOriginX - centerWorldX) * pixelsPerBlock);
                int drawY = screenCenterY + (int) Math.floor((tileOriginZ - centerWorldZ) * pixelsPerBlock);
                int drawW = (int) Math.ceil(blocksPerTile * pixelsPerBlock) + 1;
                int drawH = (int) Math.ceil(blocksPerTile * pixelsPerBlock) + 1;

                BlueMapTileClient.TileKey key = new BlueMapTileClient.TileKey(lod, tx, tz);
                if (!texCache.drawTile(graphics, key, drawX, drawY, drawW, drawH)) {
                    graphics.fill(drawX, drawY, drawX + drawW, drawY + drawH, 0xFF111827);
                    graphics.outline(drawX, drawY, drawW, drawH, 0xFF1E293B);
                }
            }
        }

        AddonConfig cfg = addon.getConfig();
        List<Component> hoveredTooltip = null;

        // Render BlueMap live markers
        if (cfg.showBlueMapMarkersOnMap) {
            for (BlueMapMarker m : addon.getMarkerClient().getMarkers()) {
                if (!cfg.enabledMarkerSets.contains(m.categoryId())) continue;
                int mx = screenCenterX + (int) Math.round((m.x() - centerWorldX) * pixelsPerBlock);
                int my = screenCenterY + (int) Math.round((m.z() - centerWorldZ) * pixelsPerBlock);
                if (mx < -10 || mx > this.width + 10 || my < mapTop - 10 || my > mapBottom + 10) continue;

                graphics.fill(mx - 3, my - 3, mx + 4, my + 4, 0xCC090D16);
                graphics.fill(mx - 2, my - 2, mx + 3, my + 3, m.colorArgb());

                if (Math.abs(mouseX - mx) <= 5 && Math.abs(mouseY - my) <= 5) {
                    hoveredTooltip = List.of(
                        GMSettingsScreen.uText(m.categoryLabel() + ": " + m.label()),
                        GMSettingsScreen.uText(String.format(Locale.ROOT, "X: %d | Y: %d | Z: %d",
                            (int) Math.round(m.x()), (int) Math.round(m.y()), (int) Math.round(m.z())))
                    );
                }
            }
        }

        // Render Speed Cameras ("Blitzer") using 128x128 HD circular speed sign texture
        if (cfg.showSpeedCamerasOnMap) {
            for (SpeedCamera cam : addon.getCameraRepository().getCameras()) {
                int cx = screenCenterX + (int) Math.round((cam.x() - centerWorldX) * pixelsPerBlock);
                int cy = screenCenterY + (int) Math.round((cam.z() - centerWorldZ) * pixelsPerBlock);
                if (cx < -16 || cx > this.width + 16 || cy < mapTop - 16 || cy > mapBottom + 16) continue;

                int signSize = 16;
                GMSettingsScreen.drawTexture(graphics, GMSettingsScreen.TEX_SPEED_SIGN, cx - signSize / 2, cy - signSize / 2, signSize, signSize);
                Component limComp = GMSettingsScreen.uText(Integer.toString(cam.effectiveSpeedLimit()));
                GMSettingsScreen.drawCenteredFittedText(graphics, this.font, limComp, cx, cy - 4, 10, 0xFF0F172A, false);

                if (Math.abs(mouseX - cx) <= 8 && Math.abs(mouseY - cy) <= 8) {
                    hoveredTooltip = new ArrayList<>();
                    hoveredTooltip.add(GMSettingsScreen.uText(I18n.get("gm-addon.screen.map.tooltip_camera", cam.name())));
                    hoveredTooltip.add(GMSettingsScreen.uText(I18n.get("gm-addon.screen.map.tooltip_limit", cam.effectiveSpeedLimit())));
                    hoveredTooltip.add(GMSettingsScreen.uText(String.format(Locale.ROOT, "Pos: %d, %d, %d",
                        (int) Math.round(cam.x()), (int) Math.round(cam.y()), (int) Math.round(cam.z()))));
                }
            }
        }

        // Render Player marker using 128x128 bilinear-filtered HD cursor and Modern badge
        if (mc.player != null) {
            int px = screenCenterX + (int) Math.round((mc.player.getX() - centerWorldX) * pixelsPerBlock);
            int py = screenCenterY + (int) Math.round((mc.player.getZ() - centerWorldZ) * pixelsPerBlock);

            graphics.pose().pushMatrix();
            graphics.pose().translate(px, py);
            if ("DOT".equalsIgnoreCase(cfg.minimapPlayerMarkerShape)) {
                GMSettingsScreen.drawTexture(graphics, GMSettingsScreen.TEX_PLAYER_DOT, -7, -7, 14, 14);
            } else {
                float yaw = mc.player.getViewYRot(partialTick);
                graphics.pose().rotate((float) Math.toRadians(yaw + 180.0f));
                GMSettingsScreen.drawTexture(graphics, GMSettingsScreen.TEX_PLAYER_ARROW, -8, -8, 16, 16);
            }
            graphics.pose().popMatrix();

            Component youComp = GMSettingsScreen.uText(I18n.get("gm-addon.common.you"));
            int youW = this.font.width(youComp) + 8;
            GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_CONTAINER_BG, px - youW / 2, py - 21, youW, 12, 4);
            GMSettingsScreen.drawCenteredFittedText(graphics, this.font, youComp, px, py - 19, youW - 4, 0xFF38BDF8, true);
        }

        graphics.disableScissor();

        // Top and bottom Modern 9-slice bars
        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_WINDOW_BG, 4, 2, this.width - 8, TOP_BAR_HEIGHT - 2, 8);
        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_WINDOW_BG, 4, mapBottom, this.width - 8, BOTTOM_BAR_HEIGHT - 2, 8);

        double cursorWorldX = centerWorldX + (mouseX - screenCenterX) / pixelsPerBlock;
        double cursorWorldZ = centerWorldZ + (mouseY - screenCenterY) / pixelsPerBlock;
        double roundedZoom = Math.round(pixelsPerBlock * 10.0) / 10.0;
        String statusLeft = I18n.get(
            "gm-addon.screen.map.status",
            (int) Math.floor(cursorWorldX),
            (int) Math.floor(cursorWorldZ),
            roundedZoom,
            lod,
            addon.getTileClient().getLoadedTileCount(),
            addon.getTileClient().getPendingRequestCount()
        );
        String dbStatus = addon.getCameraRepository().getSyncStatus();
        Component dbComp = GMSettingsScreen.uText(dbStatus);
        int dbW = Math.min(this.width / 3, this.font.width(dbComp));

        GMSettingsScreen.drawFittedText(graphics, this.font, GMSettingsScreen.uText(statusLeft), 14, mapBottom + 9, this.width - dbW - 36, 0xFFF1F5F9, true);
        GMSettingsScreen.drawFittedText(graphics, this.font, dbComp, this.width - dbW - 14, mapBottom + 9, dbW, 0xFF94A3B8, false);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (hoveredTooltip != null) {
            graphics.setComponentTooltipForNextFrame(this.font, hoveredTooltip, mouseX, mouseY);
        }
    }

    private static boolean isMapDragButton(MouseButtonEvent event) {
        return event.button() == 0 || event.button() == 1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (isMapDragButton(event) && event.y() > TOP_BAR_HEIGHT && event.y() < this.height - BOTTOM_BAR_HEIGHT) {
            draggingMap = true;
            disableFollowPlayer();
            lastMouseX = event.x();
            lastMouseY = event.y();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingMap = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingMap) {
            disableFollowPlayer();
            centerWorldX -= dragX / pixelsPerBlock;
            centerWorldZ -= dragY / pixelsPerBlock;
            lastMouseX = event.x();
            lastMouseY = event.y();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            double factor = scrollY > 0 ? 1.22 : (1.0 / 1.22);
            pixelsPerBlock = Math.clamp(pixelsPerBlock * factor, 0.06, 6.0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat.setScreen(Minecraft.getInstance(), parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
