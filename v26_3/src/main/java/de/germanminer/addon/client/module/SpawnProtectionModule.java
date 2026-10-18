package de.germanminer.addon.client.module;

import de.germanminer.addon.client.GMAddonClient;
import de.germanminer.addon.config.AddonConfig;
import de.germanminer.addon.module.HudWidgetDescriptor;
import de.germanminer.addon.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Optional;

/**
 * Modular Spawn / Respawn Protection ("Spawnschutz") feature module.
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
                return "Spawnschutz (20s)";
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
                return 180;
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
        return "Spawnschutz-Timer";
    }

    @Override
    public String getDescription() {
        return "Zeigt deinen verbleibenden Respawn-/Login-Spawnschutz als Countdown im HUD.";
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
                "Spawnschutz-Widget aktivieren",
                "Zeigt den Spawnschutz-Countdown im HUD an.",
                () -> cfg.spawnProtectionWidgetEnabled,
                val -> {
                    cfg.spawnProtectionWidgetEnabled = val;
                    addon.saveConfig();
                }
            ),
            new Setting.IntSliderSetting(
                "spawnschutz_seconds",
                "Spawnschutz Dauer",
                "Dauer des Spawnschutz-Timers in Sekunden (Standard: 20s).",
                5, 60, " s",
                () -> cfg.spawnProtectionSeconds,
                val -> {
                    cfg.spawnProtectionSeconds = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "spawnschutz_on_join",
                "Bei Server-Login starten",
                "Startet den Spawnschutz-Timer automatisch beim Verbinden auf den Server.",
                () -> cfg.triggerSpawnProtectionOnJoin,
                val -> {
                    cfg.triggerSpawnProtectionOnJoin = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "spawnschutz_on_respawn",
                "Bei Respawn starten",
                "Startet den Spawnschutz-Timer automatisch nach dem Respawn / Krankenhaus.",
                () -> cfg.triggerSpawnProtectionOnRespawn,
                val -> {
                    cfg.triggerSpawnProtectionOnRespawn = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "spawnschutz_scale",
                "Spawnschutz-Widget Skalierung",
                "Größenskalierung des Spawnschutz-Widgets im HUD.",
                0.6, 2.0, 0.05, "x",
                () -> (double) cfg.spawnProtectionScale,
                val -> {
                    cfg.spawnProtectionScale = val.floatValue();
                    addon.saveConfig();
                }
            ),
            new Setting.ActionSetting(
                "test_spawnschutz",
                "Spawnschutz-Timer jetzt testen",
                "Countdown starten",
                "Startet sofort einen Test-Durchlauf des Spawnschutz-Widgets.",
                () -> addon.getSpawnProtectionTracker().startTimer(cfg.spawnProtectionSeconds, "Test-Spawnschutz")
            )
        );
    }
}
