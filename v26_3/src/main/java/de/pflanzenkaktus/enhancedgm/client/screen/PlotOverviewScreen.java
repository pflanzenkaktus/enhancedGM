package de.pflanzenkaktus.enhancedgm.client.screen;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat;
import de.pflanzenkaktus.enhancedgm.client.module.PlotModule;
import de.pflanzenkaktus.enhancedgm.client.util.I18n;
import de.pflanzenkaktus.enhancedgm.feature.PlotTracker;
import de.pflanzenkaktus.enhancedgm.model.OwnedPlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Lists all scanned plots / apartments with the date by which they have to be entered again,
 * styled in Modern Ultra-HD design language.
 */
public class PlotOverviewScreen extends Screen {
    private static final int HEADER_HEIGHT = 38;
    private static final int FOOTER_HEIGHT = 36;
    private static final int ROW_HEIGHT = 34;

    private final GMAddonClient addon;
    private final Screen parentScreen;
    private double scrollOffset = 0.0;

    public PlotOverviewScreen(GMAddonClient addon, Screen parentScreen) {
        super(GMSettingsScreen.uText(I18n.get("gm-addon.screen.plots.title")));
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
        addRenderableWidget(new GMSettingsScreen.ModernGlassButton(
            this.width - 178, 9, 142, 20,
            GMSettingsScreen.uText(I18n.get("gm-addon.setting.plot.scan.btn")),
            GMSettingsScreen.ICON_RESET,
            btn -> {
                if (addon.getModuleManager().getPlotModule() != null) {
                    addon.getModuleManager().getPlotModule().startScan();
                }
            },
            true
        ));

        addRenderableWidget(new GMSettingsScreen.CloseButton(
            this.width - 32, 9, 22, 20,
            btn -> onClose()
        ));

        addRenderableWidget(new GMSettingsScreen.ModernGlassButton(
            this.width / 2 - 75, this.height - FOOTER_HEIGHT + 8, 150, 20,
            GMSettingsScreen.uText(I18n.get("gm-addon.common.save_and_close")),
            btn -> onClose(),
            false
        ));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xCC090D16);

        List<OwnedPlot> rows = rows();
        long now = System.currentTimeMillis();
        int topClip = HEADER_HEIGHT + 4;
        int bottomClip = this.height - FOOTER_HEIGHT - 4;
        int left = 12;
        int right = this.width - 12;
        int rowW = right - left;

        graphics.enableScissor(0, topClip, this.width, bottomClip);
        if (rows.isEmpty()) {
            GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_CARD_NORMAL, left, topClip + 8, rowW, 32, 8);
            GMSettingsScreen.drawFittedText(graphics, this.font, GMSettingsScreen.uText(I18n.get("gm-addon.screen.plots.empty")), left + 12, topClip + 20, rowW - 24, 0xFF94A3B8, false);
        }
        int y = (int) Math.round(HEADER_HEIGHT + 8 - scrollOffset);
        Identifier plotIcon = GMSettingsScreen.getCategoryIcon("plot");
        for (OwnedPlot p : rows) {
            if (y + ROW_HEIGHT >= topClip && y <= bottomClip) {
                long days = p.daysRemaining(now);
                Identifier cardTex;
                int statusColor;
                if (!p.hasBeenVisited()) {
                    cardTex = GMSettingsScreen.TEX_CARD_NORMAL;
                    statusColor = 0xFF94A3B8;
                } else if (days <= 3) {
                    cardTex = GMSettingsScreen.TEX_HUD_ALERT_RED;
                    statusColor = 0xFFFCA5A5;
                } else if (days <= addon.getConfig().plotReminderDaysBefore) {
                    cardTex = GMSettingsScreen.TEX_HUD_ALERT_AMBER;
                    statusColor = 0xFFFDE047;
                } else {
                    cardTex = GMSettingsScreen.TEX_HUD_ALERT_GREEN;
                    statusColor = 0xFF86EFAC;
                }

                GMSettingsScreen.drawNineSlice(graphics, cardTex, left, y, rowW, ROW_HEIGHT - 4, 8);
                GMSettingsScreen.drawTexture(graphics, plotIcon, left + 8, y + 7, 16, 16);

                String rawStatus;
                if (!p.hasBeenVisited()) {
                    rawStatus = I18n.get("gm-addon.screen.plots.never");
                } else if (days <= 0) {
                    rawStatus = I18n.get("gm-addon.widget.plot.expired").replaceAll("\u00a7[0-9a-fk-or]", "");
                } else {
                    rawStatus = I18n.get("gm-addon.screen.plots.until", p.deadlineDate().format(PlotModule.DATE_FORMAT), days);
                }
                Component statusComp = GMSettingsScreen.uText(rawStatus);
                int statusW = Math.min(rowW / 2 - 12, this.font.width(statusComp));
                int leftTextW = Math.max(60, rowW - statusW - 48);

                GMSettingsScreen.drawFittedText(graphics, this.font, GMSettingsScreen.uText(p.name + " (" + p.type + ")"), left + 28, y + 5, leftTextW, 0xFFF8FAFC, true);
                GMSettingsScreen.drawFittedText(graphics, this.font, GMSettingsScreen.uText(p.x + ", " + p.y + ", " + p.z + (p.area.isEmpty() ? "" : "  |  " + p.area)), left + 28, y + 16, leftTextW, 0xFF94A3B8, false);
                GMSettingsScreen.drawFittedText(graphics, this.font, statusComp, right - 10 - statusW, y + 11, statusW, statusColor, true);
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();

        // Top and bottom Modern 9-slice bars
        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_WINDOW_BG, 4, 2, this.width - 8, HEADER_HEIGHT - 2, 8);
        GMSettingsScreen.drawTexture(graphics, plotIcon, 14, 11, 16, 16);
        GMSettingsScreen.drawFittedText(graphics, this.font, GMSettingsScreen.uText(I18n.get("gm-addon.screen.plots.title")), 34, 15, this.width - 220, 0xFFF8FAFC, true);

        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_WINDOW_BG, 4, this.height - FOOTER_HEIGHT, this.width - 8, FOOTER_HEIGHT - 2, 8);

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
