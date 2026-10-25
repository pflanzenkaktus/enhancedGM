package de.pflanzenkaktus.enhancedgm.client.screen;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat;
import de.pflanzenkaktus.enhancedgm.client.module.PlotModule;
import de.pflanzenkaktus.enhancedgm.client.util.I18n;
import de.pflanzenkaktus.enhancedgm.feature.PlotTracker;
import de.pflanzenkaktus.enhancedgm.model.OwnedPlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/**
 * Lists all scanned plots / apartments with the date by which they have to be entered again.
 */
public class PlotOverviewScreen extends Screen {
    private static final int HEADER_HEIGHT = 36;
    private static final int FOOTER_HEIGHT = 32;
    private static final int ROW_HEIGHT = 30;

    private final GMAddonClient addon;
    private final Screen parentScreen;
    private double scrollOffset = 0.0;

    public PlotOverviewScreen(GMAddonClient addon, Screen parentScreen) {
        super(I18n.component("gm-addon.screen.plots.title"));
        this.addon = addon;
        this.parentScreen = parentScreen;
    }

    private List<OwnedPlot> rows() {
        return PlotTracker.sortByDeadline(addon.getPlotRepository().getPlots(
            VersionCompat.getPlayerName(Minecraft.getInstance())));
    }

    private double maxScroll() {
        int visible = this.height - HEADER_HEIGHT - FOOTER_HEIGHT - 12;
        return Math.max(0.0, rows().size() * ROW_HEIGHT - visible);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
            I18n.component("gm-addon.setting.plot.scan.btn"),
            btn -> {
                if (addon.getModuleManager().getPlotModule() != null) {
                    addon.getModuleManager().getPlotModule().startScan();
                }
            }
        ).bounds(this.width - 168, 8, 160, 20).build());

        addRenderableWidget(Button.builder(
            I18n.component("gm-addon.common.save_and_close"),
            btn -> onClose()
        ).bounds(this.width / 2 - 75, this.height - FOOTER_HEIGHT + 6, 150, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE60B1120);

        List<OwnedPlot> rows = rows();
        long now = System.currentTimeMillis();
        int topClip = HEADER_HEIGHT + 2;
        int bottomClip = this.height - FOOTER_HEIGHT - 2;
        int left = 12;
        int right = this.width - 12;

        graphics.enableScissor(0, topClip, this.width, bottomClip);
        if (rows.isEmpty()) {
            graphics.text(this.font, I18n.get("gm-addon.screen.plots.empty"), left, topClip + 12, 0xFF94A3B8, false);
        }
        int y = (int) Math.round(HEADER_HEIGHT + 8 - scrollOffset);
        for (OwnedPlot p : rows) {
            if (y + ROW_HEIGHT >= topClip && y <= bottomClip) {
                long days = p.daysRemaining(now);
                int color = !p.hasBeenVisited() ? 0xFF94A3B8
                    : days <= 3 ? 0xFFEF4444
                    : days <= addon.getConfig().plotReminderDaysBefore ? 0xFFF59E0B : 0xFF22C55E;

                graphics.fill(left, y, right, y + ROW_HEIGHT - 4, 0x881E293B);
                graphics.outline(left, y, right - left, ROW_HEIGHT - 4, color);
                graphics.text(this.font, p.name + " §7(" + p.type + ")", left + 8, y + 4, 0xFFF8FAFC, true);
                graphics.text(this.font, p.x + ", " + p.y + ", " + p.z
                    + (p.area.isEmpty() ? "" : "  -  " + p.area), left + 8, y + 15, 0xFF94A3B8, false);

                String status;
                if (!p.hasBeenVisited()) {
                    status = I18n.get("gm-addon.screen.plots.never");
                } else if (days <= 0) {
                    status = I18n.get("gm-addon.widget.plot.expired");
                } else {
                    status = I18n.get("gm-addon.screen.plots.until", p.deadlineDate().format(PlotModule.DATE_FORMAT), days);
                }
                graphics.text(this.font, status, right - 8 - this.font.width(status), y + 9, color, true);
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();

        graphics.fill(0, 0, this.width, HEADER_HEIGHT, 0xFF0F172A);
        graphics.horizontalLine(0, this.width, HEADER_HEIGHT, 0xFF38BDF8);
        graphics.text(this.font, I18n.get("gm-addon.screen.plots.title"), 12, 13, 0xFFF8FAFC, true);
        graphics.fill(0, this.height - FOOTER_HEIGHT, this.width, this.height, 0xFF0F172A);
        graphics.horizontalLine(0, this.width, this.height - FOOTER_HEIGHT, 0xFF334155);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            scrollOffset = Math.clamp(scrollOffset - scrollY * 22.0, 0.0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        VersionCompat.setScreen(Minecraft.getInstance(), parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
