package de.pflanzenkaktus.enhancedgm.client.module;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.module.HudWidgetDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Central registry and lifecycle dispatcher for all GM-Addon feature modules.
 */
public class ModuleManager {
    private final GMAddonClient addon;
    private final List<ClientFeatureModule> modules = new ArrayList<>();
    private TachoModule tachoModule;
    private PlotModule plotModule;

    public ModuleManager(GMAddonClient addon) {
        this.addon = addon;
    }

    public void registerDefaultModules() {
        registerModule(new GeneralModule(addon));
        registerModule(new MinimapModule(addon));
        this.tachoModule = new TachoModule(addon);
        registerModule(this.tachoModule);
        registerModule(new BlitzerModule(addon));
        registerModule(new SpawnProtectionModule(addon));
        this.plotModule = new PlotModule(addon);
        registerModule(this.plotModule);
        registerModule(new DebugModule(addon));
    }

    public PlotModule getPlotModule() {
        return plotModule;
    }

    public TachoModule getTachoModule() {
        return tachoModule;
    }

    public void registerModule(ClientFeatureModule module) {
        modules.add(module);
        module.onInit(addon);
    }

    public List<ClientFeatureModule> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<HudWidgetDescriptor> getHudWidgets() {
        List<HudWidgetDescriptor> list = new ArrayList<>();
        for (ClientFeatureModule m : modules) {
            m.getHudWidget().ifPresent(list::add);
        }
        return list;
    }

    public Optional<ClientFeatureModule> getModuleByWidgetId(String widgetId) {
        for (ClientFeatureModule m : modules) {
            if (m.getHudWidget().isPresent() && m.getHudWidget().get().getWidgetId().equals(widgetId)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    public void onServerJoin() {
        for (ClientFeatureModule m : modules) {
            m.onServerJoin(addon);
        }
    }

    public void onServerDisconnect() {
        for (ClientFeatureModule m : modules) {
            m.onServerDisconnect(addon);
        }
    }

    public void onClientTick(Minecraft client) {
        for (ClientFeatureModule m : modules) {
            if (m.isEnabled()) {
                m.onClientTick(addon, client);
            }
        }
    }

    public void onPlayerRespawn() {
        for (ClientFeatureModule m : modules) {
            if (m.isEnabled()) {
                m.onPlayerRespawn(addon);
            }
        }
    }

    public void onChatMessage(String plainMessage) {
        for (ClientFeatureModule m : modules) {
            if (m.isEnabled()) {
                m.onChatMessage(addon, plainMessage);
            }
        }
    }

    public void onActionBarMessage(String plainMessage) {
        for (ClientFeatureModule m : modules) {
            if (m.isEnabled()) {
                m.onActionBarMessage(addon, plainMessage);
            }
        }
    }

    public void renderAllHud(GuiGraphicsExtractor graphics, Minecraft mc, float partialTick, boolean editorPreview) {
        if (!editorPreview && de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat.getScreen(mc) == null) {
            for (ClientFeatureModule m : modules) {
                if (m.isEnabled()) {
                    m.renderWorldOverlay(addon, graphics, mc, partialTick);
                }
            }
        }
        for (ClientFeatureModule m : modules) {
            if (editorPreview || m.isEnabled()) {
                m.renderHudWidget(addon, graphics, mc, partialTick, editorPreview);
            }
        }
    }

    public void resetAllHudPositions() {
        for (HudWidgetDescriptor w : getHudWidgets()) {
            w.resetToDefault();
        }
        addon.saveConfig();
    }
}
