package de.germanminer.addon.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.germanminer.addon.client.GMAddonClient;
import de.germanminer.addon.module.HudWidgetDescriptor;
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
 * Interactive Drag-and-Drop HUD Editor screen with magnetic snapping & visual alignment guides.
 * Works generically with all HudWidgetDescriptors registered in ModuleManager.
 */
public class HudEditorScreen extends Screen {
    private static final int BOTTOM_BAR_HEIGHT = 38;
    private static final int SNAP_THRESHOLD = 7;
    private static final int SCREEN_MARGIN = 10;
    private static final int WIDGET_GAP = 6;

    private record SnapGuideLine(
        boolean vertical,
        int position,
        int start,
        int end,
        int color,
        String label
    ) {}

    private final GMAddonClient addon;
    private final Screen parentScreen;

    private HudWidgetDescriptor activeDragWidget = null;
    private double dragOffsetX = 0.0;
    private double dragOffsetY = 0.0;
    private boolean snappingEnabled = true;
    private final List<SnapGuideLine> activeGuides = new ArrayList<>();

    public HudEditorScreen(GMAddonClient addon, Screen parentScreen) {
        super(Component.literal("GM-Addon HUD-Layout-Editor"));
        this.addon = addon;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        int panelY = this.height - BOTTOM_BAR_HEIGHT;
        int btnY = panelY + 9;
        int x = 8;

        // 1. Open Full Modular Settings Screen
        addRenderableWidget(Button.builder(
            Component.literal("Modul-Einstellungen"),
            btn -> Minecraft.getInstance().gui.setScreen(new GMSettingsScreen(addon, this))
        ).bounds(x, btnY, 132, 20).build());
        x += 136;

        // 2. Toggle Magnetic Snapping
        addRenderableWidget(Button.builder(
            getSnapButtonText(),
            btn -> {
                snappingEnabled = !snappingEnabled;
                btn.setMessage(getSnapButtonText());
            }
        ).bounds(x, btnY, 118, 20).build());
        x += 122;

        // 3. Reset all widget positions
        addRenderableWidget(Button.builder(
            Component.literal("Reset Positionen"),
            btn -> addon.getModuleManager().resetAllHudPositions()
        ).bounds(x, btnY, 112, 20).build());

        // 4. Save & Close
        addRenderableWidget(Button.builder(
            Component.literal("Speichern & Fertig"),
            btn -> onClose()
        ).bounds(this.width - 122, btnY, 114, 20).build());
    }

    private Component getSnapButtonText() {
        return Component.literal(snappingEnabled ? "§bMagnet-Snap: §aAN" : "§7Magnet-Snap: §cAUS");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Update dragged widget position continuously on every rendered frame
        if (activeDragWidget != null) {
            updateDraggedWidgetPosition(mouseX, mouseY);
        } else {
            activeGuides.clear();
        }

        graphics.fill(0, 0, this.width, this.height, 0x88090D16);

        // Draw subtle background center crosshair grid when dragging
        if (activeDragWidget != null && snappingEnabled && !isShiftDown()) {
            int cx = this.width / 2;
            int cy = (this.height - BOTTOM_BAR_HEIGHT) / 2;
            graphics.verticalLine(cx, 0, this.height - BOTTOM_BAR_HEIGHT, 0x2894A3B8);
            graphics.horizontalLine(0, this.width, cy, 0x2894A3B8);
        }

        Minecraft mc = Minecraft.getInstance();
        addon.getModuleManager().renderAllHud(graphics, mc, partialTick, true);

        // Draw active magnetic snap guide lines
        for (SnapGuideLine guide : activeGuides) {
            if (guide.vertical()) {
                graphics.verticalLine(guide.position(), guide.start(), guide.end(), guide.color());
                // Glow/accent dots at ends
                graphics.fill(guide.position() - 2, guide.start(), guide.position() + 3, guide.start() + 3, guide.color());
                graphics.fill(guide.position() - 2, guide.end() - 3, guide.position() + 3, guide.end(), guide.color());
                if (guide.label() != null && !guide.label().isEmpty()) {
                    int lw = this.font.width(guide.label()) + 6;
                    int lx = Math.clamp(guide.position() - lw / 2, 4, this.width - lw - 4);
                    graphics.fill(lx, 6, lx + lw, 17, 0xE60F172A);
                    graphics.outline(lx, 6, lw, 11, guide.color());
                    graphics.text(this.font, guide.label(), lx + 3, 8, guide.color(), false);
                }
            } else {
                graphics.horizontalLine(guide.start(), guide.end(), guide.position(), guide.color());
                graphics.fill(guide.start(), guide.position() - 2, guide.start() + 3, guide.position() + 3, guide.color());
                graphics.fill(guide.end() - 3, guide.position() - 2, guide.end(), guide.position() + 3, guide.color());
                if (guide.label() != null && !guide.label().isEmpty()) {
                    int lw = this.font.width(guide.label()) + 6;
                    int ly = Math.clamp(guide.position() - 5, 4, this.height - BOTTOM_BAR_HEIGHT - 14);
                    graphics.fill(6, ly, 6 + lw, ly + 11, 0xE60F172A);
                    graphics.outline(6, ly, lw, 11, guide.color());
                    graphics.text(this.font, guide.label(), 9, ly + 2, guide.color(), false);
                }
            }
        }

        // Draw selection/drag handles & labels for every registered HUD widget module
        for (HudWidgetDescriptor widget : addon.getModuleManager().getHudWidgets()) {
            boolean isDragging = (activeDragWidget == widget);
            boolean isHovered = widget.isPointInside(mouseX, mouseY);
            boolean isSnapped = isDragging && !activeGuides.isEmpty();
            int borderColor = isSnapped ? 0xFFF43F5E : (isDragging ? 0xFF38BDF8 : (isHovered ? 0xFFFACC15 : 0xAA94A3B8));

            int wx = widget.getX();
            int wy = widget.getY();
            int ww = widget.getScaledWidth();
            int wh = widget.getScaledHeight();

            graphics.outline(wx - 2, wy - 2, ww + 4, wh + 4, borderColor);

            // Corner handles on hovered or dragged widget
            if (isDragging || isHovered) {
                graphics.fill(wx - 3, wy - 3, wx, wy, borderColor);
                graphics.fill(wx + ww, wy - 3, wx + ww + 3, wy, borderColor);
                graphics.fill(wx - 3, wy + wh, wx, wy + wh + 3, borderColor);
                graphics.fill(wx + ww, wy + wh, wx + ww + 3, wy + wh + 3, borderColor);
            }

            String tag = String.format(
                Locale.ROOT,
                "%s (%.0f%%) %s",
                widget.getDisplayName(),
                widget.getScale() * 100.0f,
                widget.isEnabled() ? "" : "[AUS]"
            );
            int tagW = this.font.width(tag) + 6;
            int tagY = Math.max(2, wy - 13);
            graphics.fill(wx - 2, tagY, wx - 2 + tagW, tagY + 11, 0xD90F172A);
            graphics.text(this.font, tag, wx + 1, tagY + 2, borderColor, false);
        }

        // Bottom control bar
        int panelY = this.height - BOTTOM_BAR_HEIGHT;
        graphics.fill(0, panelY, this.width, this.height, 0xEE0F172A);
        graphics.horizontalLine(0, this.width, panelY, 0xFF38BDF8);

        String hint = "Ziehen = Verschieben (Shift halten = Frei ohne Snap)  •  Rechtsklick = AN/AUS  •  Mausrad = Größe";
        graphics.centeredText(this.font, hint, this.width / 2, panelY - 13, 0xFFE2E8F0);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private boolean isShiftDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
            || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
    }

    private void updateDraggedWidgetPosition(double mouseX, double mouseY) {
        if (activeDragWidget == null) return;
        activeGuides.clear();

        int ww = activeDragWidget.getScaledWidth();
        int wh = activeDragWidget.getScaledHeight();
        int usableH = this.height - BOTTOM_BAR_HEIGHT;

        int maxW = Math.max(4, this.width - ww - 4);
        int maxH = Math.max(4, usableH - wh - 4);

        int rawX = Math.clamp((int) Math.round(mouseX - dragOffsetX), 2, maxW);
        int rawY = Math.clamp((int) Math.round(mouseY - dragOffsetY), 2, maxH);

        if (!snappingEnabled || isShiftDown()) {
            activeDragWidget.setPosition(rawX, rawY);
            return;
        }

        int snappedX = rawX;
        int snappedY = rawY;
        int bestDx = SNAP_THRESHOLD + 1;
        int bestDy = SNAP_THRESHOLD + 1;
        SnapGuideLine bestVerticalGuide = null;
        SnapGuideLine bestHorizontalGuide = null;

        // 1. Screen Center X & Y snapping (Magenta guide)
        int screenCenterX = this.width / 2;
        int screenCenterY = usableH / 2;

        int dxCenter = Math.abs((rawX + ww / 2) - screenCenterX);
        if (dxCenter < bestDx) {
            bestDx = dxCenter;
            snappedX = screenCenterX - ww / 2;
            bestVerticalGuide = new SnapGuideLine(true, screenCenterX, 0, usableH, 0xFFF43F5E, "MITTE X");
        }

        int dyCenter = Math.abs((rawY + wh / 2) - screenCenterY);
        if (dyCenter < bestDy) {
            bestDy = dyCenter;
            snappedY = screenCenterY - wh / 2;
            bestHorizontalGuide = new SnapGuideLine(false, screenCenterY, 0, this.width, 0xFFF43F5E, "MITTE Y");
        }

        // 2. Screen Safe-Zone Margins (10px from edges — Emerald guide)
        int dxLeftMargin = Math.abs(rawX - SCREEN_MARGIN);
        if (dxLeftMargin < bestDx) {
            bestDx = dxLeftMargin;
            snappedX = SCREEN_MARGIN;
            bestVerticalGuide = new SnapGuideLine(true, SCREEN_MARGIN, 0, usableH, 0xFF10B981, "RAND 10px");
        }
        int rightMarginX = this.width - SCREEN_MARGIN;
        int dxRightMargin = Math.abs((rawX + ww) - rightMarginX);
        if (dxRightMargin < bestDx) {
            bestDx = dxRightMargin;
            snappedX = rightMarginX - ww;
            bestVerticalGuide = new SnapGuideLine(true, rightMarginX, 0, usableH, 0xFF10B981, "RAND 10px");
        }

        int dyTopMargin = Math.abs(rawY - SCREEN_MARGIN);
        if (dyTopMargin < bestDy) {
            bestDy = dyTopMargin;
            snappedY = SCREEN_MARGIN;
            bestHorizontalGuide = new SnapGuideLine(false, SCREEN_MARGIN, 0, this.width, 0xFF10B981, "RAND 10px");
        }
        int bottomMarginY = usableH - SCREEN_MARGIN;
        int dyBottomMargin = Math.abs((rawY + wh) - bottomMarginY);
        if (dyBottomMargin < bestDy) {
            bestDy = dyBottomMargin;
            snappedY = bottomMarginY - wh;
            bestHorizontalGuide = new SnapGuideLine(false, bottomMarginY, 0, this.width, 0xFF10B981, "RAND 10px");
        }

        // 3. Widget-to-Widget Alignment & Adjacent Docking (Cyan guide)
        for (HudWidgetDescriptor other : addon.getModuleManager().getHudWidgets()) {
            if (other == activeDragWidget) continue;
            int ox = other.getX();
            int oy = other.getY();
            int ow = other.getScaledWidth();
            int oh = other.getScaledHeight();

            int spanTop = Math.min(rawY, oy) - 4;
            int spanBottom = Math.max(rawY + wh, oy + oh) + 4;
            int spanLeft = Math.min(rawX, ox) - 4;
            int spanRight = Math.max(rawX + ww, ox + ow) + 4;

            // X alignments: Left-to-Left, Right-to-Right, Center-to-Center, Dock-Right, Dock-Left
            int[][] xCandidates = {
                { ox, ox },                                   // Left aligned with other's Left
                { ox + ow - ww, ox + ow },                    // Right aligned with other's Right
                { ox + ow / 2 - ww / 2, ox + ow / 2 },        // Center aligned with other's Center
                { ox + ow + WIDGET_GAP, ox + ow + WIDGET_GAP / 2 }, // Docked right of other
                { ox - ww - WIDGET_GAP, ox - WIDGET_GAP / 2 }       // Docked left of other
            };
            for (int[] cand : xCandidates) {
                int targetX = cand[0];
                int guideX = cand[1];
                int d = Math.abs(rawX - targetX);
                if (d < bestDx) {
                    bestDx = d;
                    snappedX = targetX;
                    bestVerticalGuide = new SnapGuideLine(true, guideX, spanTop, spanBottom, 0xFF38BDF8, "SNAP");
                }
            }

            // Y alignments: Top-to-Top, Bottom-to-Bottom, Center-to-Center, Dock-Below, Dock-Above
            int[][] yCandidates = {
                { oy, oy },                                   // Top aligned with other's Top
                { oy + oh - wh, oy + oh },                    // Bottom aligned with other's Bottom
                { oy + oh / 2 - wh / 2, oy + oh / 2 },        // Center aligned with other's Center
                { oy + oh + WIDGET_GAP, oy + oh + WIDGET_GAP / 2 }, // Docked below other
                { oy - wh - WIDGET_GAP, oy - WIDGET_GAP / 2 }       // Docked above other
            };
            for (int[] cand : yCandidates) {
                int targetY = cand[0];
                int guideY = cand[1];
                int d = Math.abs(rawY - targetY);
                if (d < bestDy) {
                    bestDy = d;
                    snappedY = targetY;
                    bestHorizontalGuide = new SnapGuideLine(false, guideY, spanLeft, spanRight, 0xFF38BDF8, "SNAP");
                }
            }
        }

        if (bestVerticalGuide != null) {
            activeGuides.add(bestVerticalGuide);
        }
        if (bestHorizontalGuide != null) {
            activeGuides.add(bestHorizontalGuide);
        }

        snappedX = Math.clamp(snappedX, 2, maxW);
        snappedY = Math.clamp(snappedY, 2, maxH);
        activeDragWidget.setPosition(snappedX, snappedY);
    }

    private static boolean isLeftButton(MouseButtonEvent event) {
        return event.button() == 0 || event.button() == 1;
    }

    private static boolean isRightButton(MouseButtonEvent event) {
        return event.button() == 2 || event.button() == 3;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mx = event.x();
        double my = event.y();
        if (my >= this.height - BOTTOM_BAR_HEIGHT) {
            return false;
        }

        List<HudWidgetDescriptor> widgets = addon.getModuleManager().getHudWidgets();
        // Iterate in reverse so top-most rendered widget is picked first
        for (int i = widgets.size() - 1; i >= 0; i--) {
            HudWidgetDescriptor w = widgets.get(i);
            if (w.isPointInside(mx, my)) {
                if (isRightButton(event)) {
                    w.setEnabled(!w.isEnabled());
                    addon.saveConfig();
                    return true;
                }
                if (isLeftButton(event)) {
                    activeDragWidget = w;
                    dragOffsetX = mx - w.getX();
                    dragOffsetY = my - w.getY();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (activeDragWidget != null && isLeftButton(event)) {
            activeDragWidget = null;
            activeGuides.clear();
            addon.saveConfig();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (activeDragWidget != null) {
            updateDraggedWidgetPosition(mouseX, mouseY);
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (activeDragWidget != null) {
            updateDraggedWidgetPosition(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            float delta = scrollY > 0 ? 0.05f : -0.05f;
            List<HudWidgetDescriptor> widgets = addon.getModuleManager().getHudWidgets();
            for (int i = widgets.size() - 1; i >= 0; i--) {
                HudWidgetDescriptor w = widgets.get(i);
                if (w.isPointInside(mouseX, mouseY)) {
                    // Snap scale to clean 5% increments (e.g. 100%, 105%, 110%)
                    float nextScale = Math.round((w.getScale() + delta) * 20.0f) / 20.0f;
                    w.setScale(Math.clamp(nextScale, 0.5f, 2.2f));
                    addon.saveConfig();
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        activeDragWidget = null;
        activeGuides.clear();
        addon.saveConfig();
        Minecraft.getInstance().gui.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
