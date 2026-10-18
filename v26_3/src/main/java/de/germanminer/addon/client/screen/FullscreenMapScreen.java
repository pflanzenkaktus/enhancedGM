package de.germanminer.addon.client.screen;

import de.germanminer.addon.client.GMAddonClient;
import de.germanminer.addon.client.map.BlueMapTextureCache;
import de.germanminer.addon.config.AddonConfig;
import de.germanminer.addon.map.BlueMapTileClient;
import de.germanminer.addon.model.BlueMapMarker;
import de.germanminer.addon.model.SpeedCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Interactive Fullscreen GermanMiner BlueMap screen inside Minecraft.
 * Supports drag-to-pan, scroll-to-zoom (LOD 1 & LOD 2), Follow-Player lock,
 * Speed Camera & BlueMap POI marker filtering, and hover tooltips.
 */
public class FullscreenMapScreen extends Screen {
    private static final int TOP_BAR_HEIGHT = 34;
    private static final int BOTTOM_BAR_HEIGHT = 26;

    private final GMAddonClient addon;
    private final Screen parentScreen;

    private double centerWorldX = -885.0;
    private double centerWorldZ = -2005.0;
    private double pixelsPerBlock = 0.85;
    private boolean followPlayer = true;
    private boolean draggingMap = false;
    private double lastMouseX = 0.0;
    private double lastMouseY = 0.0;

    public FullscreenMapScreen(GMAddonClient addon, Screen parentScreen) {
        super(Component.literal("GermanMiner BlueMap"));
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
        int bx = 8;
        int by = 6;
        int bh = 20;

        // 1. Follow Player toggle
        addRenderableWidget(Button.builder(getFollowButtonText(), btn -> {
            followPlayer = !followPlayer;
            if (followPlayer && Minecraft.getInstance().player != null) {
                centerWorldX = Minecraft.getInstance().player.getX();
                centerWorldZ = Minecraft.getInstance().player.getZ();
            }
            btn.setMessage(getFollowButtonText());
        }).bounds(bx, by, 86, bh).build());
        bx += 90;

        // 2. Speed Cameras toggle
        addRenderableWidget(Button.builder(getBlitzerButtonText(cfg), btn -> {
            cfg.showSpeedCamerasOnMap = !cfg.showSpeedCamerasOnMap;
            addon.saveConfig();
            btn.setMessage(getBlitzerButtonText(cfg));
        }).bounds(bx, by, 82, bh).build());
        bx += 86;

        // 3. Tankstellen toggle
        addRenderableWidget(Button.builder(getCategoryText(cfg, "Tankstellen", "Tanken"), btn -> {
            toggleCategory(cfg, "Tankstellen");
            btn.setMessage(getCategoryText(cfg, "Tankstellen", "Tanken"));
        }).bounds(bx, by, 78, bh).build());
        bx += 82;

        // 4. Taxipunkte toggle
        addRenderableWidget(Button.builder(getCategoryText(cfg, "Taxipunkte", "Taxis"), btn -> {
            toggleCategory(cfg, "Taxipunkte");
            btn.setMessage(getCategoryText(cfg, "Taxipunkte", "Taxis"));
        }).bounds(bx, by, 72, bh).build());
        bx += 76;

        // 5. Haltestellen (Bus / S-Bahn / Kabelwagen) toggle
        addRenderableWidget(Button.builder(getTransitText(cfg), btn -> {
            boolean enabled = cfg.enabledMarkerSets.contains("Bushaltestellen");
            setTransitEnabled(cfg, !enabled);
            btn.setMessage(getTransitText(cfg));
        }).bounds(bx, by, 74, bh).build());
        bx += 78;

        // 6. Refresh Tiles & Markers button
        addRenderableWidget(Button.builder(Component.literal("Neu laden"), btn -> {
            addon.getTextureCache().clearGpuCache();
            addon.getMarkerClient().refreshAsync();
            addon.getCameraRepository().syncWithFirebaseAsync();
        }).bounds(bx, by, 70, bh).build());

        // Right-aligned navigation buttons: Settings, HUD Editor, Close
        addRenderableWidget(Button.builder(Component.literal("Einstellungen"), btn -> {
            de.germanminer.addon.client.compat.VersionCompat.setScreen(Minecraft.getInstance(), new GMSettingsScreen(addon, this));
        }).bounds(this.width - 216, by, 82, bh).build());

        addRenderableWidget(Button.builder(Component.literal("HUD-Editor"), btn -> {
            de.germanminer.addon.client.compat.VersionCompat.setScreen(Minecraft.getInstance(), new HudEditorScreen(addon, this));
        }).bounds(this.width - 130, by, 68, bh).build());

        addRenderableWidget(Button.builder(Component.literal("Fertig"), btn -> onClose())
            .bounds(this.width - 58, by, 50, bh).build());
    }

    private Component getFollowButtonText() {
        return Component.literal(followPlayer ? "Folgen: AN" : "Folgen: AUS");
    }

    private Component getBlitzerButtonText(AddonConfig cfg) {
        return Component.literal("Blitzer: " + (cfg.showSpeedCamerasOnMap ? "AN" : "AUS"));
    }

    private Component getCategoryText(AddonConfig cfg, String id, String label) {
        boolean on = cfg.enabledMarkerSets.contains(id);
        return Component.literal(label + ": " + (on ? "AN" : "AUS"));
    }

    private Component getTransitText(AddonConfig cfg) {
        boolean on = cfg.enabledMarkerSets.contains("Bushaltestellen");
        return Component.literal("ÖPNV: " + (on ? "AN" : "AUS"));
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Track drag delta smoothly on every frame
        if (draggingMap) {
            double dx = mouseX - lastMouseX;
            double dy = mouseY - lastMouseY;
            if (Math.abs(dx) > 0.01 || Math.abs(dy) > 0.01) {
                followPlayer = false;
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

        graphics.fill(0, 0, this.width, this.height, 0xFF0B1120);
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

                graphics.fill(mx - 3, my - 3, mx + 4, my + 4, 0xFF0F172A);
                graphics.fill(mx - 2, my - 2, mx + 3, my + 3, m.colorArgb());

                if (Math.abs(mouseX - mx) <= 5 && Math.abs(mouseY - my) <= 5) {
                    hoveredTooltip = List.of(
                        Component.literal(m.categoryLabel() + ": " + m.label()),
                        Component.literal(String.format(Locale.ROOT, "X: %d | Y: %d | Z: %d",
                            (int) Math.round(m.x()), (int) Math.round(m.y()), (int) Math.round(m.z())))
                    );
                }
            }
        }

        // Render Speed Cameras ("Blitzer")
        if (cfg.showSpeedCamerasOnMap) {
            for (SpeedCamera cam : addon.getCameraRepository().getCameras()) {
                int cx = screenCenterX + (int) Math.round((cam.x() - centerWorldX) * pixelsPerBlock);
                int cy = screenCenterY + (int) Math.round((cam.z() - centerWorldZ) * pixelsPerBlock);
                if (cx < -16 || cx > this.width + 16 || cy < mapTop - 16 || cy > mapBottom + 16) continue;

                graphics.fill(cx - 6, cy - 6, cx + 7, cy + 7, 0xFFDC2626);
                graphics.fill(cx - 4, cy - 4, cx + 5, cy + 5, 0xFFFFFFFF);
                String lim = Integer.toString(cam.effectiveSpeedLimit());
                graphics.centeredText(this.font, lim, cx + 1, cy - 3, 0xFF0F172A);

                if (Math.abs(mouseX - cx) <= 7 && Math.abs(mouseY - cy) <= 7) {
                    hoveredTooltip = new ArrayList<>();
                    hoveredTooltip.add(Component.literal("Blitzer: " + cam.name()));
                    hoveredTooltip.add(Component.literal("Tempolimit: " + cam.effectiveSpeedLimit() + " km/h"));
                    hoveredTooltip.add(Component.literal(String.format(Locale.ROOT, "Pos: %d, %d, %d",
                        (int) Math.round(cam.x()), (int) Math.round(cam.y()), (int) Math.round(cam.z()))));
                }
            }
        }

        // Render Player marker
        if (mc.player != null) {
            int px = screenCenterX + (int) Math.round((mc.player.getX() - centerWorldX) * pixelsPerBlock);
            int py = screenCenterY + (int) Math.round((mc.player.getZ() - centerWorldZ) * pixelsPerBlock);
            graphics.fill(px - 5, py - 5, px + 6, py + 6, 0xFF0F172A);
            graphics.fill(px - 4, py - 4, px + 5, py + 5, 0xFF38BDF8);
            graphics.centeredText(this.font, "DU", px + 1, py - 14, 0xFF38BDF8);
        }

        graphics.disableScissor();

        // Top and bottom chrome bars
        graphics.fill(0, 0, this.width, TOP_BAR_HEIGHT, 0xEE0F172A);
        graphics.horizontalLine(0, this.width, TOP_BAR_HEIGHT, 0xFF334155);

        graphics.fill(0, mapBottom, this.width, this.height, 0xEE0F172A);
        graphics.horizontalLine(0, this.width, mapBottom, 0xFF334155);

        double cursorWorldX = centerWorldX + (mouseX - screenCenterX) / pixelsPerBlock;
        double cursorWorldZ = centerWorldZ + (mouseY - screenCenterY) / pixelsPerBlock;
        String statusLeft = String.format(
            Locale.ROOT,
            "Mauszeiger: X %d | Z %d  •  Zoom: %.2fx (LOD %d)  •  Kacheln: %d (%d lädt)",
            (int) Math.floor(cursorWorldX),
            (int) Math.floor(cursorWorldZ),
            pixelsPerBlock,
            lod,
            addon.getTileClient().getLoadedTileCount(),
            addon.getTileClient().getPendingRequestCount()
        );
        graphics.text(this.font, statusLeft, 10, mapBottom + 9, 0xFFE2E8F0, true);

        String dbStatus = addon.getCameraRepository().getSyncStatus();
        graphics.text(this.font, dbStatus, this.width - this.font.width(dbStatus) - 10, mapBottom + 9, 0xFF94A3B8, false);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (hoveredTooltip != null) {
            graphics.setComponentTooltipForNextFrame(this.font, hoveredTooltip, mouseX, mouseY);
        }
    }

    private static boolean isLeftClick(MouseButtonEvent event) {
        return event.button() == 0 || event.button() == 1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (isLeftClick(event) && event.y() > TOP_BAR_HEIGHT && event.y() < this.height - BOTTOM_BAR_HEIGHT) {
            draggingMap = true;
            lastMouseX = event.x();
            lastMouseY = event.y();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (isLeftClick(event)) {
            draggingMap = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingMap && isLeftClick(event)) {
            followPlayer = false;
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
        de.germanminer.addon.client.compat.VersionCompat.setScreen(Minecraft.getInstance(), parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
