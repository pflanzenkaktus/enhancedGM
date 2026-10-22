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
 * Modular BlueMap Minimap & Fullscreen Map feature module.
 */
public class MinimapModule implements ClientFeatureModule {
    private final GMAddonClient addon;
    private final HudWidgetDescriptor widgetDescriptor;

    public MinimapModule(GMAddonClient addon) {
        this.addon = addon;
        this.widgetDescriptor = new HudWidgetDescriptor() {
            @Override
            public String getWidgetId() {
                return "minimap";
            }

            @Override
            public String getDisplayName() {
                return I18n.get("gm-addon.widget.minimap.name");
            }

            @Override
            public boolean isEnabled() {
                return addon.getConfig().minimapEnabled;
            }

            @Override
            public void setEnabled(boolean enabled) {
                addon.getConfig().minimapEnabled = enabled;
                addon.saveConfig();
            }

            @Override
            public int getX() {
                return addon.getConfig().minimapX;
            }

            @Override
            public int getY() {
                return addon.getConfig().minimapY;
            }

            @Override
            public void setPosition(int x, int y) {
                addon.getConfig().minimapX = x;
                addon.getConfig().minimapY = y;
            }

            @Override
            public float getScale() {
                return addon.getConfig().minimapScale;
            }

            @Override
            public void setScale(float scale) {
                addon.getConfig().minimapScale = scale;
            }

            @Override
            public int getBaseWidth() {
                return addon.getConfig().minimapSize;
            }

            @Override
            public int getBaseHeight() {
                return addon.getConfig().minimapSize + 22;
            }

            @Override
            public void resetToDefault() {
                AddonConfig cfg = addon.getConfig();
                cfg.minimapX = 12;
                cfg.minimapY = 12;
                cfg.minimapScale = 1.0f;
            }
        };
    }

    @Override
    public String getId() {
        return "minimap";
    }

    @Override
    public String getDisplayName() {
        return I18n.get("gm-addon.module.minimap.name");
    }

    @Override
    public String getDescription() {
        return I18n.get("gm-addon.module.minimap.desc");
    }

    @Override
    public boolean isEnabled() {
        return addon.getConfig().minimapEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        addon.getConfig().minimapEnabled = enabled;
        addon.saveConfig();
    }

    @Override
    public Optional<HudWidgetDescriptor> getHudWidget() {
        return Optional.of(widgetDescriptor);
    }

    @Override
    public void onServerJoin(GMAddonClient addon) {
        addon.getMarkerClient().refreshAsync();
    }

    @Override
    public void renderHudWidget(GMAddonClient addon, GuiGraphicsExtractor graphics, Minecraft mc, float partialTick, boolean editorPreview) {
        addon.getHudRenderer().renderMinimapWidget(graphics, mc, partialTick, editorPreview);
    }

    @Override
    public List<Setting<?>> getSettings() {
        AddonConfig cfg = addon.getConfig();
        return List.of(
            new Setting.BooleanSetting(
                "minimap_enabled",
                I18n.get("gm-addon.setting.minimap.enabled"),
                I18n.get("gm-addon.setting.minimap.enabled.desc"),
                () -> cfg.minimapEnabled,
                val -> {
                    cfg.minimapEnabled = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "minimap_rotate",
                I18n.get("gm-addon.setting.minimap.rotate_with_player"),
                I18n.get("gm-addon.setting.minimap.rotate_with_player.desc"),
                () -> cfg.minimapRotateWithPlayer,
                val -> {
                    cfg.minimapRotateWithPlayer = val;
                    addon.saveConfig();
                }
            ),
            new Setting.IntSliderSetting(
                "minimap_size",
                I18n.get("gm-addon.setting.minimap.size"),
                I18n.get("gm-addon.setting.minimap.size.desc"),
                80, 240, " px",
                () -> cfg.minimapSize,
                val -> {
                    cfg.minimapSize = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "minimap_zoom_blocks",
                I18n.get("gm-addon.setting.minimap.zoom"),
                I18n.get("gm-addon.setting.minimap.zoom.desc"),
                50.0, 500.0, 10.0, I18n.get("gm-addon.common.blocks_unit"),
                () -> cfg.minimapZoomBlocks,
                val -> {
                    cfg.minimapZoomBlocks = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "minimap_scale",
                I18n.get("gm-addon.setting.minimap.scale"),
                I18n.get("gm-addon.setting.minimap.scale.desc"),
                0.6, 2.0, 0.05, "x",
                () -> (double) cfg.minimapScale,
                val -> {
                    cfg.minimapScale = val.floatValue();
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "show_blitzer_on_map",
                I18n.get("gm-addon.setting.blitzer.show_on_map"),
                I18n.get("gm-addon.setting.blitzer.show_on_map.desc"),
                () -> cfg.showSpeedCamerasOnMap,
                val -> {
                    cfg.showSpeedCamerasOnMap = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "show_bluemap_markers",
                I18n.get("gm-addon.setting.minimap.show_markers"),
                I18n.get("gm-addon.setting.minimap.show_markers.desc"),
                () -> cfg.showBlueMapMarkersOnMap,
                val -> {
                    cfg.showBlueMapMarkersOnMap = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "marker_tankstellen",
                I18n.get("gm-addon.setting.minimap.marker_gas"),
                I18n.get("gm-addon.setting.minimap.marker_gas.desc"),
                () -> cfg.enabledMarkerSets.contains("Tankstellen"),
                val -> toggleCategory(cfg, "Tankstellen", val)
            ),
            new Setting.BooleanSetting(
                "marker_taxipunkte",
                I18n.get("gm-addon.setting.minimap.marker_taxis"),
                I18n.get("gm-addon.setting.minimap.marker_taxis.desc"),
                () -> cfg.enabledMarkerSets.contains("Taxipunkte"),
                val -> toggleCategory(cfg, "Taxipunkte", val)
            ),
            new Setting.BooleanSetting(
                "marker_bushaltestellen",
                I18n.get("gm-addon.setting.minimap.marker_bus"),
                I18n.get("gm-addon.setting.minimap.marker_bus.desc"),
                () -> cfg.enabledMarkerSets.contains("Bushaltestellen"),
                val -> toggleCategory(cfg, "Bushaltestellen", val)
            ),
            new Setting.BooleanSetting(
                "marker_sbahn",
                I18n.get("gm-addon.setting.minimap.marker_train"),
                I18n.get("gm-addon.setting.minimap.marker_train.desc"),
                () -> cfg.enabledMarkerSets.contains("S-Bahnhaltestellen"),
                val -> toggleCategory(cfg, "S-Bahnhaltestellen", val)
            ),
            new Setting.IntSliderSetting(
                "tile_cache_ttl",
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
                "refresh_bluemap_now",
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

    private void toggleCategory(AddonConfig cfg, String id, boolean enable) {
        cfg.enabledMarkerSets.remove(id);
        if (enable) {
            cfg.enabledMarkerSets.add(id);
        }
        addon.saveConfig();
    }
}
