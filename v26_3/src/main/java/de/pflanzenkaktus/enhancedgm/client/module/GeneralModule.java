package de.pflanzenkaktus.enhancedgm.client.module;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.util.I18n;
import de.pflanzenkaktus.enhancedgm.config.AddonConfig;
import de.pflanzenkaktus.enhancedgm.module.Setting;

import java.util.List;

/**
 * General addon settings module (Language, Layout Reset).
 * Developer overrides and server domain patterns moved to DebugModule.
 */
public class GeneralModule implements ClientFeatureModule {
    private final GMAddonClient addon;

    public GeneralModule(GMAddonClient addon) {
        this.addon = addon;
    }

    @Override
    public String getId() {
        return "general";
    }

    @Override
    public String getDisplayName() {
        return I18n.get("gm-addon.module.general.name");
    }

    @Override
    public String getDescription() {
        return I18n.get("gm-addon.module.general.desc");
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // Always enabled core module
    }

    @Override
    public List<Setting<?>> getSettings() {
        AddonConfig cfg = addon.getConfig();
        return List.of(
            new Setting.CycleSetting(
                "addon_language",
                I18n.get("gm-addon.setting.language"),
                I18n.get("gm-addon.setting.language.desc"),
                List.of("AUTO", "EN", "DE"),
                () -> cfg.language,
                val -> {
                    cfg.language = val;
                    addon.saveConfig();
                }
            ),
            new Setting.ActionSetting(
                "reset_all_hud",
                I18n.get("gm-addon.setting.reset_all_hud"),
                I18n.get("gm-addon.setting.reset_all_hud.btn"),
                I18n.get("gm-addon.setting.reset_all_hud.desc"),
                () -> addon.getModuleManager().resetAllHudPositions()
            )
        );
    }
}
