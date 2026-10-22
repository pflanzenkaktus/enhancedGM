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
 * community speed camera cloud synchronization.
 */
public class CommunitySyncPromptScreen extends Screen {
    private final GMAddonClient addon;
    private final Screen parent;

    public CommunitySyncPromptScreen(GMAddonClient addon, Screen parent) {
        super(I18n.component("gm-addon.dialog.community_sync.title"));
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
                I18n.component("gm-addon.dialog.community_sync.enable"),
                btn -> enableSync()
        ).bounds(cardX + 20, btnY, btnWidth, btnHeight).build();

        Button disableBtn = Button.builder(
                I18n.component("gm-addon.dialog.community_sync.disable"),
                btn -> disableSync()
        ).bounds(cardX + cardWidth - btnWidth - 20, btnY, btnWidth, btnHeight).build();

        addRenderableWidget(enableBtn);
        addRenderableWidget(disableBtn);
    }

    private void enableSync() {
        addon.getConfig().enableFirebaseCloudSync = true;
        addon.getConfig().firebaseConsentPromptDismissed = true;
        addon.saveConfig();
        addon.getCameraRepository().syncWithFirebaseAsync();
        closeDialog();
    }

    private void disableSync() {
        addon.getConfig().enableFirebaseCloudSync = false;
        addon.getConfig().firebaseConsentPromptDismissed = true;
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
        graphics.centeredText(this.font, I18n.component("gm-addon.dialog.community_sync.title"), this.width / 2, cardY + 14, 0xFF38BDF8);
        graphics.horizontalLine(cardX + 16, cardX + cardWidth - 16, cardY + 28, 0xFF334155);

        // Subtitle
        graphics.centeredText(this.font, I18n.component("gm-addon.dialog.community_sync.subtitle"), this.width / 2, cardY + 36, 0xFFF8FAFC);

        // Information text lines
        graphics.centeredText(this.font, I18n.component("gm-addon.dialog.community_sync.line1"), this.width / 2, cardY + 58, 0xFF94A3B8);
        graphics.centeredText(this.font, I18n.component("gm-addon.dialog.community_sync.line2"), this.width / 2, cardY + 70, 0xFF94A3B8);
        graphics.centeredText(this.font, I18n.component("gm-addon.dialog.community_sync.line3"), this.width / 2, cardY + 92, 0xFF64748B);

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
        if (!addon.getConfig().firebaseConsentPromptDismissed) {
            disableSync();
        } else {
            closeDialog();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
