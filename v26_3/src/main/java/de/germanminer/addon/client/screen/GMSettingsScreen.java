package de.germanminer.addon.client.screen;

import de.germanminer.addon.client.GMAddonClient;
import de.germanminer.addon.client.module.ClientFeatureModule;
import de.germanminer.addon.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full modular Settings Screen for GM-Addon, accessible from Mod Menu or via Hotkey (O / H).
 * Automatically generates UI controls (Toggles, Sliders, Text boxes, Action buttons)
 * for every registered ClientFeatureModule.
 */
public class GMSettingsScreen extends Screen {
    private static final int HEADER_HEIGHT = 36;
    private static final int FOOTER_HEIGHT = 32;
    private static final int SIDEBAR_WIDTH = 150;
    private static final int ROW_HEIGHT = 36;

    private record SettingRow(Setting<?> setting, AbstractWidget control, int baseLocalY) {}

    private final GMAddonClient addon;
    private final Screen parentScreen;

    private int selectedModuleIndex = 0;
    private double scrollOffset = 0.0;
    private double maxScroll = 0.0;
    private final List<SettingRow> activeRows = new ArrayList<>();

    public GMSettingsScreen(GMAddonClient addon, Screen parentScreen) {
        super(Component.literal("GermanMiner Addon — Einstellungen"));
        this.addon = addon;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        this.activeRows.clear();

        List<ClientFeatureModule> modules = addon.getModuleManager().getModules();
        if (selectedModuleIndex >= modules.size()) {
            selectedModuleIndex = 0;
        }

        // Top Bar: Quick navigation to HUD Layout Editor & Fullscreen Map
        addRenderableWidget(Button.builder(
            Component.literal("HUD-Positionen bearbeiten (Ziehen)"),
            btn -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen(addon, this))
        ).bounds(this.width - 294, 8, 196, 20).build());

        addRenderableWidget(Button.builder(
            Component.literal("Fullscreen-Karte"),
            btn -> Minecraft.getInstance().gui.setScreen(new FullscreenMapScreen(addon, this))
        ).bounds(this.width - 94, 8, 86, 20).build());

        // Left Sidebar: Module Category buttons
        int catY = HEADER_HEIGHT + 10;
        for (int i = 0; i < modules.size(); i++) {
            final int idx = i;
            ClientFeatureModule mod = modules.get(i);
            String prefix = (i == selectedModuleIndex) ? "» " : "  ";
            addRenderableWidget(Button.builder(
                Component.literal(prefix + mod.getDisplayName()),
                btn -> {
                    selectedModuleIndex = idx;
                    scrollOffset = 0.0;
                    rebuildWidgets();
                }
            ).bounds(8, catY, SIDEBAR_WIDTH - 16, 22).build());
            catY += 26;
        }

        // Right Content Area: Controls for the selected module's settings
        if (!modules.isEmpty()) {
            ClientFeatureModule activeModule = modules.get(selectedModuleIndex);
            List<Setting<?>> settings = activeModule.getSettings();

            int contentX = SIDEBAR_WIDTH + 14;
            int contentWidth = Math.max(200, this.width - contentX - 16);
            int controlWidth = Math.min(180, Math.max(130, contentWidth / 2 - 10));
            int controlX = contentX + contentWidth - controlWidth - 6;

            int localY = 0;
            for (Setting<?> setting : settings) {
                AbstractWidget widget = createControlForSetting(setting, controlX, HEADER_HEIGHT + 12 + localY, controlWidth);
                if (widget != null) {
                    addRenderableWidget(widget);
                    activeRows.add(new SettingRow(setting, widget, localY));
                    localY += ROW_HEIGHT;
                }
            }

            int visibleHeight = Math.max(60, this.height - HEADER_HEIGHT - FOOTER_HEIGHT - 16);
            this.maxScroll = Math.max(0.0, localY - visibleHeight);
            this.scrollOffset = Math.clamp(this.scrollOffset, 0.0, this.maxScroll);
            updateRowPositions();
        }

        // Footer: Done button
        addRenderableWidget(Button.builder(
            Component.literal("Speichern & Schließen"),
            btn -> onClose()
        ).bounds(this.width / 2 - 75, this.height - FOOTER_HEIGHT + 6, 150, 20).build());
    }

    private AbstractWidget createControlForSetting(Setting<?> setting, int x, int y, int width) {
        int height = 20;
        if (setting instanceof Setting.BooleanSetting boolSetting) {
            return Button.builder(formatBooleanText(boolSetting.getter().get()), btn -> {
                boolean next = !boolSetting.getter().get();
                boolSetting.setter().accept(next);
                btn.setMessage(formatBooleanText(next));
            }).bounds(x, y, width, height).build();
        } else if (setting instanceof Setting.IntSliderSetting intSetting) {
            int cur = intSetting.getter().get();
            double norm = (double) (cur - intSetting.min()) / Math.max(1.0, (double) (intSetting.max() - intSetting.min()));
            return new AbstractSliderButton(x, y, width, height,
                Component.literal(cur + intSetting.unitSuffix()),
                Math.clamp(norm, 0.0, 1.0)) {
                @Override
                protected void updateMessage() {
                    int val = (int) Math.round(intSetting.min() + this.value * (intSetting.max() - intSetting.min()));
                    setMessage(Component.literal(val + intSetting.unitSuffix()));
                }

                @Override
                protected void applyValue() {
                    int val = (int) Math.round(intSetting.min() + this.value * (intSetting.max() - intSetting.min()));
                    intSetting.setter().accept(val);
                }
            };
        } else if (setting instanceof Setting.DoubleSliderSetting dblSetting) {
            double cur = dblSetting.getter().get();
            double norm = (cur - dblSetting.min()) / Math.max(0.0001, (dblSetting.max() - dblSetting.min()));
            return new AbstractSliderButton(x, y, width, height,
                Component.literal(formatDoubleValue(cur, dblSetting.step(), dblSetting.unitSuffix())),
                Math.clamp(norm, 0.0, 1.0)) {
                @Override
                protected void updateMessage() {
                    double val = snapStep(dblSetting.min() + this.value * (dblSetting.max() - dblSetting.min()), dblSetting.step());
                    setMessage(Component.literal(formatDoubleValue(val, dblSetting.step(), dblSetting.unitSuffix())));
                }

                @Override
                protected void applyValue() {
                    double val = snapStep(dblSetting.min() + this.value * (dblSetting.max() - dblSetting.min()), dblSetting.step());
                    dblSetting.setter().accept(val);
                }
            };
        } else if (setting instanceof Setting.StringSetting strSetting) {
            EditBox box = new EditBox(this.font, x, y, width, height, Component.literal(strSetting.label()));
            box.setMaxLength(256);
            String current = strSetting.getter().get();
            box.setValue(current != null ? current : "");
            box.setResponder(strSetting.setter());
            return box;
        } else if (setting instanceof Setting.CycleSetting cycleSetting) {
            String initial = cycleSetting.getter().get();
            return Button.builder(Component.literal("§b" + initial), btn -> {
                List<String> opts = cycleSetting.options();
                if (opts == null || opts.isEmpty()) return;
                String cur = cycleSetting.getter().get();
                int idx = opts.indexOf(cur);
                String next = opts.get((idx + 1) % opts.size());
                cycleSetting.setter().accept(next);
                btn.setMessage(Component.literal("§b" + next));
            }).bounds(x, y, width, height).build();
        } else if (setting instanceof Setting.ActionSetting actionSetting) {
            return Button.builder(Component.literal(actionSetting.buttonText()), btn -> actionSetting.action().run())
                .bounds(x, y, width, height).build();
        }
        return null;
    }

    private void updateRowPositions() {
        int topClip = HEADER_HEIGHT + 6;
        int bottomClip = this.height - FOOTER_HEIGHT - 6;
        for (SettingRow row : activeRows) {
            int screenY = (int) Math.round(HEADER_HEIGHT + 12 + row.baseLocalY() - scrollOffset);
            row.control().setY(screenY);
            row.control().visible = (screenY >= topClip - 4 && screenY + 20 <= bottomClip + 4);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Dark backdrop
        graphics.fill(0, 0, this.width, this.height, 0xE60B1120);

        // Sidebar background
        graphics.fill(0, HEADER_HEIGHT, SIDEBAR_WIDTH, this.height - FOOTER_HEIGHT, 0xCC0F172A);
        graphics.verticalLine(SIDEBAR_WIDTH, HEADER_HEIGHT, this.height - FOOTER_HEIGHT, 0xFF334155);

        // Content area rows
        int contentX = SIDEBAR_WIDTH + 14;
        int contentWidth = Math.max(200, this.width - contentX - 16);
        int topClip = HEADER_HEIGHT + 2;
        int bottomClip = this.height - FOOTER_HEIGHT - 2;

        graphics.enableScissor(SIDEBAR_WIDTH + 1, topClip, this.width, bottomClip);
        for (SettingRow row : activeRows) {
            int rowY = row.control().getY() - 5;
            if (rowY + ROW_HEIGHT < topClip || rowY > bottomClip) continue;

            graphics.fill(contentX, rowY, contentX + contentWidth, rowY + ROW_HEIGHT - 4, 0x881E293B);
            graphics.outline(contentX, rowY, contentWidth, ROW_HEIGHT - 4, 0xFF334155);

            graphics.text(this.font, row.setting().label(), contentX + 8, rowY + 5, 0xFFF8FAFC, true);
            String desc = row.setting().description();
            int maxDescW = Math.max(80, row.control().getX() - contentX - 16);
            if (this.font.width(desc) > maxDescW) {
                desc = this.font.plainSubstrByWidth(desc, maxDescW - 8) + "...";
            }
            graphics.text(this.font, desc, contentX + 8, rowY + 18, 0xFF94A3B8, false);
        }
        graphics.disableScissor();

        // Header & Footer bars
        graphics.fill(0, 0, this.width, HEADER_HEIGHT, 0xFF0F172A);
        graphics.horizontalLine(0, this.width, HEADER_HEIGHT, 0xFF38BDF8);
        graphics.text(this.font, "GermanMiner Addon — Modul-Einstellungen", 12, 13, 0xFFF8FAFC, true);

        graphics.fill(0, this.height - FOOTER_HEIGHT, this.width, this.height, 0xFF0F172A);
        graphics.horizontalLine(0, this.width, this.height - FOOTER_HEIGHT, 0xFF334155);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX > SIDEBAR_WIDTH && maxScroll > 0.0 && scrollY != 0.0) {
            scrollOffset = Math.clamp(scrollOffset - scrollY * 22.0, 0.0, maxScroll);
            updateRowPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static Component formatBooleanText(boolean val) {
        return Component.literal(val ? "AN (Aktiv)" : "AUS");
    }

    private static double snapStep(double value, double step) {
        if (step <= 0.0) return value;
        return Math.round(value / step) * step;
    }

    private static String formatDoubleValue(double val, double step, String suffix) {
        if (step >= 1.0) {
            return String.format(Locale.ROOT, "%d%s", Math.round(val), suffix);
        }
        return String.format(Locale.ROOT, "%.2f%s", val, suffix);
    }

    @Override
    public void onClose() {
        addon.saveConfig();
        Minecraft.getInstance().gui.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
