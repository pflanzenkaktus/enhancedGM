package de.germanminer.addon.client.module;

import de.germanminer.addon.client.GMAddonClient;
import de.germanminer.addon.config.AddonConfig;
import de.germanminer.addon.module.Setting;

import java.util.List;

/**
 * General addon settings module (Server filter, Force Enable Everywhere, Second-Monitor Web Map Bridge).
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
        return "Allgemein & Server";
    }

    @Override
    public String getDescription() {
        return "Server-Erkennung (germanminer.de), Second-Monitor Web-Bridge & Layout.";
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
            new Setting.BooleanSetting(
                "force_enable_everywhere",
                "Überall aktivieren (auch Singleplayer)",
                "Wenn AUS, ist das Addon nur auf germanminer.de aktiv.",
                () -> cfg.forceEnableEverywhere,
                val -> {
                    cfg.forceEnableEverywhere = val;
                    addon.saveConfig();
                }
            ),
            new Setting.StringSetting(
                "server_domain_pattern",
                "Server-Domain Filter",
                "Erkennt den Server anhand der IP/Domain (Standard: germanminer.de)",
                () -> cfg.serverDomainPattern,
                val -> {
                    cfg.serverDomainPattern = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "enable_localhost_bridge",
                "2nd-Monitor Web-Map Bridge (127.0.0.1)",
                "Startet lokalen Webserver für zentrierte Browser-Karte auf zweitem Monitor.",
                () -> cfg.enableLocalhostWebBridge,
                val -> {
                    cfg.enableLocalhostWebBridge = val;
                    addon.saveConfig();
                    if (val) {
                        addon.getWebMapBridge().startIfEnabled();
                    } else {
                        addon.getWebMapBridge().stop();
                    }
                }
            ),
            new Setting.IntSliderSetting(
                "localhost_bridge_port",
                "Web-Map Bridge Port",
                "Port für die lokale Browser-Karte (Standard: 2087)",
                1025, 9999, "",
                () -> cfg.localhostBridgePort,
                val -> {
                    cfg.localhostBridgePort = val;
                    addon.saveConfig();
                }
            ),
            new Setting.ActionSetting(
                "reset_all_hud",
                "HUD-Widget Positionen zurücksetzen",
                "Standard-Layout wiederherstellen",
                "Setzt Minimap, Blitzer-Warnung und Spawnschutz auf ihre Standardpositionen.",
                () -> addon.getModuleManager().resetAllHudPositions()
            )
        );
    }
}
