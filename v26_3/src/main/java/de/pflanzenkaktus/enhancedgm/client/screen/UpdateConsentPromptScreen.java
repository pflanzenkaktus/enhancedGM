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
 * Initial setup popup dialog prompting the player whether to enable
 * automatic GitHub release update notifications.
 * Opt-in prompt: asks before connecting to GitHub.
 */
public class UpdateConsentPromptScreen extends Screen {
    private final GMAddonClient addon;
    private final Screen parent;

    public UpdateConsentPromptScreen(GMAddonClient addon, Screen parent) {
        super(I18n.component("gm-addon.dialog.update_check.title"));
        this.addon = addon;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int cardWidth = 340;
        int cardHeight = 175;
        int cardX = (this.width - cardWidth) / 2;
        int cardY = (this.height - cardHeight) / 2;

        int btnWidth = 140;
        int btnHeight = 22;
        int btnY = cardY + cardHeight - btnHeight - 16;

        Button enableBtn = Button.builder(
                I18n.component("gm-addon.dialog.update_check.enable"),
                btn -> enableUpdates()
        ).bounds(cardX + 20, btnY, btnWidth, btnHeight).build();

        Button disableBtn = Button.builder(
                I18n.component("gm-addon.dialog.update_check.disable"),
                btn -> disableUpdates()
        ).bounds(cardX + cardWidth - btnWidth - 20, btnY, btnWidth, btnHeight).build();

        addRenderableWidget(enableBtn);
        addRenderableWidget(disableBtn);
    }

    private void enableUpdates() {
        addon.getConfig().checkUpdates = true;
        addon.getConfig().updateConsentPromptDismissed = true;
        addon.saveConfig();
        addon.getUpdateChecker().checkForUpdatesAsync(false);
        closeDialog();
    }

    private void disableUpdates() {
        addon.getConfig().checkUpdates = false;
        addon.getConfig().updateConsentPromptDismissed = true;
        addon.saveConfig();
        closeDialog();
    }

    private void closeDialog() {
        VersionCompat.setScreen(Minecraft.getInstance(), parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Dim dark background
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);

        int cardWidth = 340;
        int cardHeight = 175;
        int cardX = (this.width - cardWidth) / 2;
        int cardY = (this.height - cardHeight) / 2;

        // Card container
        graphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xF00F172A);
        graphics.outline(cardX, cardY, cardWidth, cardHeight, 0xFF38BDF8);

        // Header
        graphics.centeredText(this.font, I18n.component("gm-addon.dialog.update_check.title"), this.width / 2, cardY + 14, 0xFF38BDF8);
        graphics.horizontalLine(cardX + 16, cardX + cardWidth - 16, cardY + 28, 0xFF334155);

        // Subtitle
        graphics.centeredText(this.font, I18n.component("gm-addon.dialog.update_check.subtitle"), this.width / 2, cardY + 36, 0xFFF8FAFC);

        // Information text lines
        graphics.centeredText(this.font, I18n.component("gm-addon.dialog.update_check.line1"), this.width / 2, cardY + 58, 0xFF94A3B8);
        graphics.centeredText(this.font, I18n.component("gm-addon.dialog.update_check.line2"), this.width / 2, cardY + 70, 0xFF94A3B8);
        graphics.centeredText(this.font, I18n.component("gm-addon.dialog.update_check.line3"), this.width / 2, cardY + 92, 0xFF64748B);

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
        if (!addon.getConfig().updateConsentPromptDismissed) {
            disableUpdates();
        } else {
            closeDialog();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
