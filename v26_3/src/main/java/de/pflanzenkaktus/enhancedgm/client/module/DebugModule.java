package de.pflanzenkaktus.enhancedgm.client.module;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.util.I18n;
import de.pflanzenkaktus.enhancedgm.config.AddonConfig;
import de.pflanzenkaktus.enhancedgm.module.Setting;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Advanced & Developer Debug Module containing test actions,
 * calibration sliders, simulation tools and domain overrides.
 */
public class DebugModule implements ClientFeatureModule {
    private final GMAddonClient addon;

    public DebugModule(GMAddonClient addon) {
        this.addon = addon;
    }

    @Override
    public String getId() {
        return "debug";
    }

    @Override
    public String getDisplayName() {
        return I18n.get("gm-addon.module.debug.name");
    }

    @Override
    public String getDescription() {
        return I18n.get("gm-addon.module.debug.desc");
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // Always enabled
    }

    @Override
    public List<Setting<?>> getSettings() {
        AddonConfig cfg = addon.getConfig();
        PlotModule plotMod = addon.getModuleManager().getPlotModule();

        return List.of(
            // Overrides & Server Filter
            new Setting.BooleanSetting(
                "force_enable_everywhere",
                I18n.get("gm-addon.setting.force_enable_everywhere"),
                I18n.get("gm-addon.setting.force_enable_everywhere.desc"),
                () -> cfg.forceEnableEverywhere,
                val -> {
                    cfg.forceEnableEverywhere = val;
                    addon.saveConfig();
                }
            ),
            new Setting.StringSetting(
                "server_domain_pattern",
                I18n.get("gm-addon.setting.server_domain_pattern"),
                I18n.get("gm-addon.setting.server_domain_pattern.desc"),
                () -> cfg.serverDomainPattern,
                val -> {
                    cfg.serverDomainPattern = val;
                    addon.saveConfig();
                }
            ),

            // Simulationen & Test-Trigger
            new Setting.ActionSetting(
                "test_spawnschutz",
                I18n.get("gm-addon.setting.spawn_protection.test"),
                I18n.get("gm-addon.setting.spawn_protection.test.btn"),
                I18n.get("gm-addon.setting.spawn_protection.test.desc"),
                () -> addon.getSpawnProtectionTracker().startTimer(
                    cfg.spawnProtectionSeconds,
                    I18n.get("gm-addon.setting.spawn_protection.test_label")
                )
            ),
            new Setting.ActionSetting(
                "plot_test_reminder",
                I18n.get("gm-addon.setting.plot.test"),
                I18n.get("gm-addon.setting.plot.test.btn"),
                I18n.get("gm-addon.setting.plot.test.desc"),
                () -> {
                    if (plotMod != null) {
                        plotMod.sendReminder(Minecraft.getInstance(), true);
                    }
                }
            ),
            new Setting.ActionSetting(
                "plot_simulate",
                I18n.get("gm-addon.setting.plot.simulate"),
                I18n.get("gm-addon.setting.plot.simulate.btn"),
                I18n.get("gm-addon.setting.plot.simulate.desc"),
                () -> {
                    if (plotMod != null) {
                        plotMod.simulateTestExpiry();
                    }
                }
            ),
            new Setting.ActionSetting(
                "plot_reset_test",
                I18n.get("gm-addon.setting.plot.reset_test"),
                I18n.get("gm-addon.setting.plot.reset_test.btn"),
                I18n.get("gm-addon.setting.plot.reset_test.desc"),
                () -> {
                    if (plotMod != null) {
                        plotMod.resetTestExpiry();
                    }
                }
            ),

            // Kalibrierung & Erweiterte Parameter
            new Setting.DoubleSliderSetting(
                "speed_calibration",
                I18n.get("gm-addon.setting.tacho.speed_calibration"),
                I18n.get("gm-addon.setting.tacho.speed_calibration.desc"),
                1.80, 3.60, 0.05, "x",
                () -> cfg.speedCalibrationFactor,
                val -> {
                    cfg.speedCalibrationFactor = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "blitzer_dedupe_radius",
                I18n.get("gm-addon.setting.blitzer.dedupe_radius"),
                I18n.get("gm-addon.setting.blitzer.dedupe_radius.desc"),
                10.0, 80.0, 2.0, " m",
                () -> cfg.blitzerDedupeRadiusBlocks,
                val -> {
                    cfg.blitzerDedupeRadiusBlocks = val;
                    addon.saveConfig();
                }
            ),
            new Setting.IntSliderSetting(
                "plot_radius",
                I18n.get("gm-addon.setting.plot.radius"),
                I18n.get("gm-addon.setting.plot.radius.desc"),
                20, 500, " m",
                () -> cfg.plotMatchRadiusBlocks,
                val -> {
                    cfg.plotMatchRadiusBlocks = val;
                    addon.saveConfig();
                }
            ),

            // Cache & BlueMap
            new Setting.IntSliderSetting(
                "cache_ttl",
                I18n.get("gm-addon.setting.minimap.cache_ttl"),
                I18n.get("gm-addon.setting.minimap.cache_ttl.desc"),
                5, 240, " min",
                () -> cfg.tileCacheTtlMinutes,
                val -> {
                    cfg.tileCacheTtlMinutes = val;
                    addon.saveConfig();
                }
            ),
            new Setting.ActionSetting(
                "refresh_now",
                I18n.get("gm-addon.setting.minimap.refresh_now"),
                I18n.get("gm-addon.setting.minimap.refresh_now.btn"),
                I18n.get("gm-addon.setting.minimap.refresh_now.desc"),
                () -> {
                    addon.getTextureCache().clearGpuCache();
                    addon.getMarkerClient().refreshAsync();
                }
            )
        );
    }
}
