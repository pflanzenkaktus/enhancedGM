package de.pflanzenkaktus.enhancedgm.client.screen;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat;
import de.pflanzenkaktus.enhancedgm.client.util.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/**
 * Popup shown when no plots have been scanned yet, asking the player to scan them,
 * styled in Modern design language.
 */
public class PlotScanPromptScreen extends Screen {
    private static final int CARD_W = 350;
    private static final int CARD_H = 148;

    private final GMAddonClient addon;

    public PlotScanPromptScreen(GMAddonClient addon) {
        super(GMSettingsScreen.uText(I18n.get("gm-addon.dialog.plot_scan.title")));
        this.addon = addon;
    }

    @Override
    protected void init() {
        super.init();
        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;
        int btnW = 148;
        int btnY = cardY + CARD_H - 22 - 14;

        addRenderableWidget(new GMSettingsScreen.ModernGlassButton(
            cardX + 18, btnY, btnW, 22,
            GMSettingsScreen.uText(I18n.get("gm-addon.dialog.plot_scan.now")),
            GMSettingsScreen.getCategoryIcon("plot"),
            btn -> {
                VersionCompat.setScreen(Minecraft.getInstance(), null);
                if (addon.getModuleManager().getPlotModule() != null) {
                    addon.getModuleManager().getPlotModule().startScan();
                }
            },
            true
        ));

        addRenderableWidget(new GMSettingsScreen.ModernGlassButton(
            cardX + CARD_W - btnW - 18, btnY, btnW, 22,
            GMSettingsScreen.uText(I18n.get("gm-addon.dialog.plot_scan.later")),
            btn -> onClose(),
            false
        ));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;
        int maxTextW = CARD_W - 28;

        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_WINDOW_BG, cardX, cardY, CARD_W, CARD_H, 8);
        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_CONTAINER_BG, cardX + 10, cardY + 32, CARD_W - 20, 66, 8);

        GMSettingsScreen.drawCenteredFittedText(graphics, this.font, GMSettingsScreen.uText(I18n.get("gm-addon.dialog.plot_scan.title")), this.width / 2, cardY + 13, maxTextW, 0xFF38BDF8, true);
        GMSettingsScreen.drawCenteredFittedText(graphics, this.font, GMSettingsScreen.uText(I18n.get("gm-addon.dialog.plot_scan.line1")), this.width / 2, cardY + 40, maxTextW, 0xFFF8FAFC, true);
        GMSettingsScreen.drawCenteredFittedText(graphics, this.font, GMSettingsScreen.uText(I18n.get("gm-addon.dialog.plot_scan.line2")), this.width / 2, cardY + 56, maxTextW, 0xFFCBD5E1, false);
        GMSettingsScreen.drawCenteredFittedText(graphics, this.font, GMSettingsScreen.uText(I18n.get("gm-addon.dialog.plot_scan.line3")), this.width / 2, cardY + 72, maxTextW, 0xFF94A3B8, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (VersionCompat.isEscape(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        VersionCompat.setScreen(Minecraft.getInstance(), null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
