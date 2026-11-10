package de.pflanzenkaktus.enhancedgm.client.screen;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat;
import de.pflanzenkaktus.enhancedgm.client.util.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/**
 * Initial setup popup dialog prompting the player whether to enable
 * community speed camera cloud synchronization, styled in Modern design language.
 */
public class CommunitySyncPromptScreen extends Screen {
    private static final int CARD_W = 350;
    private static final int CARD_H = 175;

    private final GMAddonClient addon;
    private final Screen parent;

    public CommunitySyncPromptScreen(GMAddonClient addon, Screen parent) {
        super(GMSettingsScreen.uText(I18n.get("gm-addon.dialog.community_sync.title")));
        this.addon = addon;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;

        int btnWidth = 148;
        int btnHeight = 22;
        int btnY = cardY + CARD_H - btnHeight - 16;

        addRenderableWidget(new GMSettingsScreen.ModernGlassButton(
            cardX + 18, btnY, btnWidth, btnHeight,
            GMSettingsScreen.uText(I18n.get("gm-addon.dialog.community_sync.enable")),
            GMSettingsScreen.getSettingIcon("cloud"),
            btn -> enableSync(),
            true
        ));

        addRenderableWidget(new GMSettingsScreen.ModernGlassButton(
            cardX + CARD_W - btnWidth - 18, btnY, btnWidth, btnHeight,
            GMSettingsScreen.uText(I18n.get("gm-addon.dialog.community_sync.disable")),
            btn -> disableSync(),
            false
        ));
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
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);

        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;
        int maxTextW = CARD_W - 28;

        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_WINDOW_BG, cardX, cardY, CARD_W, CARD_H, 8);
        GMSettingsScreen.drawNineSlice(graphics, GMSettingsScreen.TEX_CONTAINER_BG, cardX + 10, cardY + 34, CARD_W - 20, 82, 8);

        GMSettingsScreen.drawCenteredFittedText(graphics, this.font, GMSettingsScreen.uText(I18n.get("gm-addon.dialog.community_sync.title")), this.width / 2, cardY + 13, maxTextW, 0xFF38BDF8, true);
        GMSettingsScreen.drawCenteredFittedText(graphics, this.font, GMSettingsScreen.uText(I18n.get("gm-addon.dialog.community_sync.subtitle")), this.width / 2, cardY + 42, maxTextW, 0xFFF8FAFC, true);
        GMSettingsScreen.drawCenteredFittedText(graphics, this.font, GMSettingsScreen.uText(I18n.get("gm-addon.dialog.community_sync.line1")), this.width / 2, cardY + 62, maxTextW, 0xFFCBD5E1, false);
        GMSettingsScreen.drawCenteredFittedText(graphics, this.font, GMSettingsScreen.uText(I18n.get("gm-addon.dialog.community_sync.line2")), this.width / 2, cardY + 75, maxTextW, 0xFFCBD5E1, false);
        GMSettingsScreen.drawCenteredFittedText(graphics, this.font, GMSettingsScreen.uText(I18n.get("gm-addon.dialog.community_sync.line3")), this.width / 2, cardY + 96, maxTextW, 0xFF94A3B8, false);

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
