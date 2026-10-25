package de.pflanzenkaktus.enhancedgm.client.module;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.util.I18n;
import de.pflanzenkaktus.enhancedgm.config.AddonConfig;
import de.pflanzenkaktus.enhancedgm.module.HudWidgetDescriptor;
import de.pflanzenkaktus.enhancedgm.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Optional;

/**
 * Modular Spawn / Respawn Protection ("Spawnschutz") feature module.
 * Test actions moved to DebugModule.
 */
public class SpawnProtectionModule implements ClientFeatureModule {
    private final GMAddonClient addon;
    private final HudWidgetDescriptor widgetDescriptor;

    public SpawnProtectionModule(GMAddonClient addon) {
        this.addon = addon;
        this.widgetDescriptor = new HudWidgetDescriptor() {
            @Override
            public String getWidgetId() {
                return "spawn_protection";
            }

            @Override
            public String getDisplayName() {
                return I18n.get("gm-addon.widget.spawn_protection.name");
            }

            @Override
            public boolean isEnabled() {
                return addon.getConfig().spawnProtectionWidgetEnabled;
            }

            @Override
            public void setEnabled(boolean enabled) {
                addon.getConfig().spawnProtectionWidgetEnabled = enabled;
                addon.saveConfig();
            }

            @Override
            public int getX() {
                return addon.getConfig().spawnProtectionX;
            }

            @Override
            public int getY() {
                return addon.getConfig().spawnProtectionY;
            }

            @Override
            public void setPosition(int x, int y) {
                addon.getConfig().spawnProtectionX = x;
                addon.getConfig().spawnProtectionY = y;
            }

            @Override
            public float getScale() {
                return addon.getConfig().spawnProtectionScale;
            }

            @Override
            public void setScale(float scale) {
                addon.getConfig().spawnProtectionScale = scale;
            }

            @Override
            public int getBaseWidth() {
                return 210;
            }

            @Override
            public int getBaseHeight() {
                return 34;
            }

            @Override
            public void resetToDefault() {
                AddonConfig cfg = addon.getConfig();
                cfg.spawnProtectionX = 12;
                cfg.spawnProtectionY = 172;
                cfg.spawnProtectionScale = 1.0f;
            }
        };
    }

    @Override
    public String getId() {
        return "spawn_protection";
    }

    @Override
    public String getDisplayName() {
        return I18n.get("gm-addon.module.spawn_protection.name");
    }

    @Override
    public String getDescription() {
        return I18n.get("gm-addon.module.spawn_protection.desc");
    }

    @Override
    public boolean isEnabled() {
        return addon.getConfig().spawnProtectionWidgetEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        addon.getConfig().spawnProtectionWidgetEnabled = enabled;
        addon.saveConfig();
    }

    @Override
    public Optional<HudWidgetDescriptor> getHudWidget() {
        return Optional.of(widgetDescriptor);
    }

    @Override
    public void onServerJoin(GMAddonClient addon) {
        addon.getSpawnProtectionTracker().triggerOnServerJoin();
    }

    @Override
    public void onServerDisconnect(GMAddonClient addon) {
        addon.getSpawnProtectionTracker().cancelTimer();
    }

    @Override
    public void onPlayerRespawn(GMAddonClient addon) {
        addon.getSpawnProtectionTracker().triggerOnRespawn();
    }

    @Override
    public void onChatMessage(GMAddonClient addon, String plainMessage) {
        addon.getSpawnProtectionTracker().onChatMessage(plainMessage);
    }

    @Override
    public void renderHudWidget(GMAddonClient addon, GuiGraphicsExtractor graphics, Minecraft mc, float partialTick, boolean editorPreview) {
        addon.getHudRenderer().renderSpawnProtectionWidget(graphics, mc, editorPreview);
    }

    @Override
    public List<Setting<?>> getSettings() {
        AddonConfig cfg = addon.getConfig();
        return List.of(
            new Setting.BooleanSetting(
                "spawnschutz_enabled",
                I18n.get("gm-addon.setting.spawn_protection.enabled"),
                I18n.get("gm-addon.setting.spawn_protection.enabled.desc"),
                () -> cfg.spawnProtectionWidgetEnabled,
                val -> {
                    cfg.spawnProtectionWidgetEnabled = val;
                    addon.saveConfig();
                }
            ),
            new Setting.IntSliderSetting(
                "spawnschutz_seconds",
                I18n.get("gm-addon.setting.spawn_protection.duration"),
                I18n.get("gm-addon.setting.spawn_protection.duration.desc"),
                5, 60, " s",
                () -> cfg.spawnProtectionSeconds,
                val -> {
                    cfg.spawnProtectionSeconds = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "spawnschutz_on_join",
                I18n.get("gm-addon.setting.spawn_protection.on_join"),
                I18n.get("gm-addon.setting.spawn_protection.on_join.desc"),
                () -> cfg.triggerSpawnProtectionOnJoin,
                val -> {
                    cfg.triggerSpawnProtectionOnJoin = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "spawnschutz_on_respawn",
                I18n.get("gm-addon.setting.spawn_protection.on_respawn"),
                I18n.get("gm-addon.setting.spawn_protection.on_respawn.desc"),
                () -> cfg.triggerSpawnProtectionOnRespawn,
                val -> {
                    cfg.triggerSpawnProtectionOnRespawn = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "spawnschutz_scale",
                I18n.get("gm-addon.setting.spawn_protection.scale"),
                I18n.get("gm-addon.setting.spawn_protection.scale.desc"),
                0.6, 2.0, 0.05, "x",
                () -> (double) cfg.spawnProtectionScale,
                val -> {
                    cfg.spawnProtectionScale = val.floatValue();
                    addon.saveConfig();
                }
            )
        );
    }
}
