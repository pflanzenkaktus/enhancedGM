package de.germanminer.addon.client.module;

import de.germanminer.addon.client.GMAddonClient;
import de.germanminer.addon.module.HudWidgetDescriptor;
import de.germanminer.addon.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Optional;

/**
 * Base contract for any modular feature in GM-Addon.
 * To add a new feature later (e.g., DamageLog / 30s Notwehr tracker), implement this interface
 * and register the module in ModuleManager.
 */
public interface ClientFeatureModule {
    String getId();
    String getDisplayName();
    String getDescription();

    boolean isEnabled();
    void setEnabled(boolean enabled);

    List<Setting<?>> getSettings();

    default Optional<HudWidgetDescriptor> getHudWidget() {
        return Optional.empty();
    }

    default void onInit(GMAddonClient addon) {}
    default void onServerJoin(GMAddonClient addon) {}
    default void onServerDisconnect(GMAddonClient addon) {}
    default void onClientTick(GMAddonClient addon, Minecraft client) {}
    default void onPlayerRespawn(GMAddonClient addon) {}
    default void onChatMessage(GMAddonClient addon, String plainMessage) {}
    default void onActionBarMessage(GMAddonClient addon, String plainMessage) {}

    default void renderWorldOverlay(GMAddonClient addon, GuiGraphicsExtractor graphics, Minecraft mc, float partialTick) {}
    default void renderHudWidget(GMAddonClient addon, GuiGraphicsExtractor graphics, Minecraft mc, float partialTick, boolean editorPreview) {}
}
