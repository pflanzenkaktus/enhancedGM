package de.pflanzenkaktus.enhancedgm.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.screen.GMSettingsScreen;

/**
 * Integrates GM-Addon with TerraformersMC's Mod Menu so the configuration icon
 * in the Mods list opens GMSettingsScreen.
 */
public class GMModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new GMSettingsScreen(GMAddonClient.getInstance(), parent);
    }
}
