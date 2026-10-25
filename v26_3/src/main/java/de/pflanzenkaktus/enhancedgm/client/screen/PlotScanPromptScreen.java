package de.pflanzenkaktus.enhancedgm.client.screen;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat;
import de.pflanzenkaktus.enhancedgm.client.util.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/**
 * Popup shown when no plots have been scanned yet, asking the player to scan them.
 */
public class PlotScanPromptScreen extends Screen {
    private static final int CARD_W = 340;
    private static final int CARD_H = 130;

    private final GMAddonClient addon;

    public PlotScanPromptScreen(GMAddonClient addon) {
        super(I18n.component("gm-addon.dialog.plot_scan.title"));
        this.addon = addon;
    }

    @Override
    protected void init() {
        super.init();
        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;
        int btnW = 140;
        int btnY = cardY + CARD_H - 22 - 14;

        addRenderableWidget(Button.builder(
            I18n.component("gm-addon.dialog.plot_scan.now"),
            btn -> {
                VersionCompat.setScreen(Minecraft.getInstance(), null);
                if (addon.getModuleManager().getPlotModule() != null) {
                    addon.getModuleManager().getPlotModule().startScan();
                }
            }
        ).bounds(cardX + 20, btnY, btnW, 22).build());

        addRenderableWidget(Button.builder(
            I18n.component("gm-addon.dialog.plot_scan.later"),
            btn -> onClose()
        ).bounds(cardX + CARD_W - btnW - 20, btnY, btnW, 22).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;
        graphics.fill(cardX, cardY, cardX + CARD_W, cardY + CARD_H, 0xF00F172A);
        graphics.outline(cardX, cardY, CARD_W, CARD_H, 0xFF38BDF8);
        graphics.centeredText(this.font, I18n.component("gm-addon.dialog.plot_scan.title"), this.width / 2, cardY + 14, 0xFF38BDF8);
        graphics.horizontalLine(cardX + 16, cardX + CARD_W - 16, cardY + 28, 0xFF334155);
        graphics.centeredText(this.font, I18n.component("gm-addon.dialog.plot_scan.line1"), this.width / 2, cardY + 40, 0xFFF8FAFC);
        graphics.centeredText(this.font, I18n.component("gm-addon.dialog.plot_scan.line2"), this.width / 2, cardY + 54, 0xFF94A3B8);
        graphics.centeredText(this.font, I18n.component("gm-addon.dialog.plot_scan.line3"), this.width / 2, cardY + 66, 0xFF94A3B8);
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
