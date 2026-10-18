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
                return "BlueMap Minimap";
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
        return "BlueMap Minimap & Karte";
    }

    @Override
    public String getDescription() {
        return "Zentrierte GermanMiner Live-Minimap und Fullscreen-Karte (Taste M).";
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
                "HUD Minimap anzeigen",
                "Zeigt die BlueMap Minimap im Spiel-HUD an.",
                () -> cfg.minimapEnabled,
                val -> {
                    cfg.minimapEnabled = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "minimap_rotate",
                "Mit Blickrichtung drehen (Rotate with Player)",
                "Wenn AUS, bleibt Norden immer oben (North-Up).",
                () -> cfg.minimapRotateWithPlayer,
                val -> {
                    cfg.minimapRotateWithPlayer = val;
                    addon.saveConfig();
                }
            ),
            new Setting.IntSliderSetting(
                "minimap_size",
                "Minimap Grundgröße",
                "Breite/Höhe der Minimap in Pixeln.",
                80, 240, " px",
                () -> cfg.minimapSize,
                val -> {
                    cfg.minimapSize = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "minimap_zoom_blocks",
                "Minimap Sichtfeld (Zoom)",
                "Sichtbarer Durchmesser auf der Minimap in Blöcken.",
                50.0, 500.0, 10.0, " Blöcke",
                () -> cfg.minimapZoomBlocks,
                val -> {
                    cfg.minimapZoomBlocks = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "minimap_scale",
                "Minimap Skalierung",
                "Skalierungsfaktor des Minimap-Widgets.",
                0.6, 2.0, 0.05, "x",
                () -> (double) cfg.minimapScale,
                val -> {
                    cfg.minimapScale = val.floatValue();
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "show_blitzer_on_map",
                "Blitzer auf Karte anzeigen",
                "Zeigt alle bekannten Geschwindigkeitskameras auf Minimap & Karte.",
                () -> cfg.showSpeedCamerasOnMap,
                val -> {
                    cfg.showSpeedCamerasOnMap = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "show_bluemap_markers",
                "Server-Marker (Tankstellen, Taxis, ÖPNV) anzeigen",
                "Zeigt die offiziellen GermanMiner BlueMap-POIs auf der Karte.",
                () -> cfg.showBlueMapMarkersOnMap,
                val -> {
                    cfg.showBlueMapMarkersOnMap = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "marker_tankstellen",
                "POI: Tankstellen einblenden",
                "Tankstellen auf Minimap & Fullscreen-Karte anzeigen.",
                () -> cfg.enabledMarkerSets.contains("Tankstellen"),
                val -> toggleCategory(cfg, "Tankstellen", val)
            ),
            new Setting.BooleanSetting(
                "marker_taxipunkte",
                "POI: Taxipunkte einblenden",
                "Taxipunkte auf Minimap & Fullscreen-Karte anzeigen.",
                () -> cfg.enabledMarkerSets.contains("Taxipunkte"),
                val -> toggleCategory(cfg, "Taxipunkte", val)
            ),
            new Setting.BooleanSetting(
                "marker_bushaltestellen",
                "POI: Bushaltestellen einblenden",
                "Bushaltestellen auf Minimap & Fullscreen-Karte anzeigen.",
                () -> cfg.enabledMarkerSets.contains("Bushaltestellen"),
                val -> toggleCategory(cfg, "Bushaltestellen", val)
            ),
            new Setting.BooleanSetting(
                "marker_sbahn",
                "POI: S-Bahnhaltestellen einblenden",
                "S-Bahnhaltestellen auf Minimap & Fullscreen-Karte anzeigen.",
                () -> cfg.enabledMarkerSets.contains("S-Bahnhaltestellen"),
                val -> toggleCategory(cfg, "S-Bahnhaltestellen", val)
            ),
            new Setting.IntSliderSetting(
                "tile_cache_ttl",
                "BlueMap Tile-Cache Intervall",
                "Nach wie vielen Minuten Karten-Kacheln neu vom Server geladen werden.",
                5, 240, " min",
                () -> cfg.tileCacheTtlMinutes,
                val -> {
                    cfg.tileCacheTtlMinutes = val;
                    addon.saveConfig();
                }
            ),
            new Setting.ActionSetting(
                "refresh_bluemap_now",
                "BlueMap Kacheln & Marker neu laden",
                "Jetzt aktualisieren",
                "Löscht den lokalen Tile-Cache und lädt alle Kacheln & POIs frisch von map.germanminer.de.",
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
