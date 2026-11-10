package de.pflanzenkaktus.enhancedgm.client.screen;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.module.ClientFeatureModule;
import de.pflanzenkaktus.enhancedgm.client.util.I18n;
import de.pflanzenkaktus.enhancedgm.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modern Settings Screen for enhancedGM.
 * Features:
 * - TrueType Calibri Bold HD typography (gm-addon:modern) at 8x FreeType oversampling + hardware bilinear filtering
 * - 8x-supersampled (256x256) Bilinear-Filtered 9-slice UI surfaces (zero visible pixel staircases)
 * - 128x128 Bilinear-Filtered flat vector icons for categories, settings, search, and close button
 * - Spring-animated Modern pill toggle switches with Gaussian-shadow circular knobs
 * - Rich ~92-94% opaque dark Modern slate surfaces for maximum readability over the 3D world
 * - Adaptive auto-fit typography so labels and button texts always fit cleanly inside their containers
 */
public class GMSettingsScreen extends Screen {

    public static final Identifier MODERN_FONT = Identifier.fromNamespaceAndPath("gm-addon", "modern");
    public static final FontDescription.Resource MODERN_FONT_DESC = new FontDescription.Resource(MODERN_FONT);

    // 8x-Supersampled Bilinear-Filtered 9-Slice UI Textures
    public static final Identifier TEX_WINDOW_BG = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/window_bg.png");
    public static final Identifier TEX_CONTAINER_BG = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/container_bg.png");
    public static final Identifier TEX_CARD_NORMAL = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/card_normal.png");
    public static final Identifier TEX_CARD_HOVER = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/card_hover.png");
    public static final Identifier TEX_CAT_NORMAL = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/cat_normal.png");
    public static final Identifier TEX_CAT_HOVER = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/cat_hover.png");
    public static final Identifier TEX_CAT_ACTIVE = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/cat_active.png");
    public static final Identifier TEX_BTN_ACCENT = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/btn_accent.png");
    public static final Identifier TEX_BTN_ACCENT_HOVER = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/btn_accent_hover.png");
    public static final Identifier TEX_BTN_SECONDARY = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/btn_secondary.png");
    public static final Identifier TEX_BTN_SECONDARY_HOVER = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/btn_secondary_hover.png");
    public static final Identifier TEX_BTN_DANGER = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/btn_danger.png");
    public static final Identifier TEX_BTN_DANGER_HOVER = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/btn_danger_hover.png");
    public static final Identifier TEX_INPUT_NORMAL = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/input_normal.png");
    public static final Identifier TEX_INPUT_FOCUSED = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/input_focused.png");
    public static final Identifier TEX_SLIDER_TRACK = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/slider_track.png");
    public static final Identifier TEX_SLIDER_FILL = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/slider_fill.png");
    public static final Identifier TEX_SLIDER_RAIL_BG = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/slider_rail_bg.png");
    public static final Identifier TEX_SLIDER_RAIL_FILL = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/slider_rail_fill.png");
    public static final Identifier TEX_SLIDER_THUMB = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/slider_thumb.png");
    public static final Identifier TEX_SLIDER_THUMB_HOVER = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/slider_thumb_hover.png");

    // Dedicated HUD Alert & Bar 9-Slice Textures
    public static final Identifier TEX_HUD_ALERT_RED = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/hud_alert_red.png");
    public static final Identifier TEX_HUD_ALERT_AMBER = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/hud_alert_amber.png");
    public static final Identifier TEX_HUD_ALERT_GREEN = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/hud_alert_green.png");
    public static final Identifier TEX_BAR_FILL_RED = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/bar_fill_red.png");
    public static final Identifier TEX_BAR_FILL_AMBER = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/bar_fill_amber.png");
    public static final Identifier TEX_BAR_FILL_GREEN = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/bar_fill_green.png");

    // 8x-Supersampled Full-Widget Textures
    public static final Identifier TEX_SWITCH_OFF = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/switch_off.png");
    public static final Identifier TEX_SWITCH_ON = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/switch_on.png");
    public static final Identifier TEX_SWITCH_KNOB = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/switch_knob.png");
    public static final Identifier TEX_BADGE_BG = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/badge_bg.png");
    public static final Identifier TEX_SPEED_SIGN = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/speed_sign.png");
    public static final Identifier TEX_TACHO_DIAL = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/tacho_dial.png");
    public static final Identifier TEX_PLAYER_ARROW = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/hud_player_arrow.png");
    public static final Identifier TEX_PLAYER_DOT = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/ui/hud_player_dot.png");

    // Special UI Icons
    public static final Identifier ICON_SEARCH = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/search.png");
    public static final Identifier ICON_CLOSE = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/close.png");
    public static final Identifier ICON_HUD = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/hud.png");
    public static final Identifier ICON_MINIMAP = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/minimap.png");
    public static final Identifier ICON_RESET = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/reset.png");
    public static final Identifier ICON_PROTECTION = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/protection.png");
    public static final Identifier ICON_PLOT = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/plot.png");
    public static final Identifier ICON_TACHO = Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/tacho.png");

    public record SettingEntry(Setting<?> setting, String categoryId, String categoryName) {}
    private record SettingCardRow(SettingEntry entry, AbstractWidget control, Identifier icon, Component formattedLabel, int maxLabelW, int cardX, int baseLocalY, int cardW, int cardH) {}

    private final GMAddonClient addon;
    private final Screen parentScreen;

    private int selectedModuleIndex = 0;
    private String searchQuery = "";
    private double scrollOffset = 0.0;
    private double maxScroll = 0.0;

    // Window Layout Geometry
    private int winX;
    private int winY;
    private int winW;
    private int winH;

    private int sideX;
    private int sideY;
    private int sideW;
    private int sideH;

    private int mainX;
    private int mainY;
    private int mainW;
    private int mainH;

    private int gridY;
    private int gridH;

    private EditBox searchBox;
    private final List<SettingCardRow> activeCards = new ArrayList<>();
    private SettingEntry hoveredEntry = null;

    public GMSettingsScreen(GMAddonClient addon, Screen parentScreen) {
        super(uText(I18n.get("gm-addon.screen.settings.title")));
        this.addon = addon;
        this.parentScreen = parentScreen;
    }

    public static Component uText(String text) {
        if (text == null) return Component.empty();
        return Component.literal(text).withStyle(s -> s.withFont(MODERN_FONT_DESC));
    }

    /**
     * Draws text in the modern HD font that automatically scales down smoothly if it exceeds maxWidth.
     */
    public static void drawFittedText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int maxWidth, int color, boolean shadow) {
        if (maxWidth <= 8) return;
        int w = font.width(text);
        if (w <= maxWidth) {
            graphics.text(font, text, x, y, color, shadow);
        } else {
            float scale = Math.clamp((float) maxWidth / (float) w, 0.65f, 1.0f);
            float yOffset = (8.0f - 8.0f * scale) * 0.5f;
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) x, (float) y + yOffset);
            graphics.pose().scale(scale, scale);
            graphics.text(font, text, 0, 0, color, shadow);
            graphics.pose().popMatrix();
        }
    }

    /**
     * Draws centered text in the modern HD font that automatically scales down smoothly if it exceeds maxWidth.
     */
    public static void drawCenteredFittedText(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int maxWidth, int color, boolean shadow) {
        if (maxWidth <= 8) return;
        int w = font.width(text);
        if (w <= maxWidth) {
            graphics.text(font, text, centerX - w / 2, y, color, shadow);
        } else {
            float scale = Math.clamp((float) maxWidth / (float) w, 0.65f, 1.0f);
            float yOffset = (8.0f - 8.0f * scale) * 0.5f;
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) centerX - (w * scale) * 0.5f, (float) y + yOffset);
            graphics.pose().scale(scale, scale);
            graphics.text(font, text, 0, 0, color, shadow);
            graphics.pose().popMatrix();
        }
    }

    @Override
    protected void init() {
        this.activeCards.clear();
        this.clearWidgets();

        // Responsive Floating Modern Window — guaranteed never to exceed screen bounds
        int maxAllowedW = Math.max(320, this.width - 16);
        int maxAllowedH = Math.max(200, this.height - 16);
        this.winW = Math.min(maxAllowedW, Math.clamp(this.width - 24, 460, 820));
        this.winH = Math.min(maxAllowedH, Math.clamp(this.height - 24, 240, 470));
        this.winX = (this.width - this.winW) / 2;
        this.winY = Math.max(8, (this.height - this.winH) / 2);

        this.sideW = (this.winW >= 540) ? 182 : 158;
        this.sideX = this.winX + 10;
        this.sideY = this.winY + 34;
        this.sideH = this.winH - 44;

        this.mainX = this.sideX + this.sideW + 8;
        this.mainW = this.winX + this.winW - this.mainX - 10;
        this.mainY = this.winY + 34;
        this.mainH = this.winH - 44;

        this.gridY = this.mainY + 34;
        this.gridH = this.mainH - 72; // Bottom 34px reserved for hover detail bar

        List<ClientFeatureModule> modules = addon.getModuleManager().getModules();
        if (selectedModuleIndex >= modules.size()) {
            selectedModuleIndex = 0;
        }

        // Top Header Actions - Modern Blue Pill & Secondary Pill Buttons with 12x12 HD Icons
        int headerBtnY = this.winY + 7;
        Component hudBtnText = uText(I18n.get("gm-addon.screen.settings.btn_hud"));
        Component mapBtnText = uText(I18n.get("gm-addon.screen.settings.btn_map"));
        int hudBtnW = Math.max(88, this.font.width(hudBtnText) + 28);
        int mapBtnW = Math.max(76, this.font.width(mapBtnText) + 28);
        int closeBtnX = this.winX + this.winW - 30;
        int mapBtnX = closeBtnX - mapBtnW - 5;
        int hudBtnX = mapBtnX - hudBtnW - 5;

        addRenderableWidget(new ModernGlassButton(
            hudBtnX, headerBtnY, hudBtnW, 20,
            hudBtnText, ICON_HUD,
            btn -> de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat.setScreen(
                Minecraft.getInstance(), new HudEditorScreen(addon, this)
            ),
            true
        ));

        addRenderableWidget(new ModernGlassButton(
            mapBtnX, headerBtnY, mapBtnW, 20,
            mapBtnText, ICON_MINIMAP,
            btn -> de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat.setScreen(
                Minecraft.getInstance(), new FullscreenMapScreen(addon, this)
            ),
            false
        ));

        // Clean HD Vector Close Button
        addRenderableWidget(new CloseButton(
            closeBtnX, headerBtnY, 20, 20,
            btn -> onClose()
        ));

        // Left Sidebar: Modern Modern Category Pills with 128x128 HD Icons
        int catY = this.sideY + 22;
        int availSideH = this.sideH - 26;
        int catStep = Math.min(29, Math.max(24, availSideH / Math.max(1, modules.size())));
        int catH = Math.min(25, catStep - 3);

        for (int i = 0; i < modules.size(); i++) {
            final int idx = i;
            ClientFeatureModule mod = modules.get(i);
            boolean isSelected = (i == selectedModuleIndex && (searchQuery == null || searchQuery.isBlank()));

            addRenderableWidget(new ModernCategoryButton(
                this.sideX + 6, catY, this.sideW - 12, catH,
                uText(mod.getDisplayName()),
                mod.getId(),
                btn -> {
                    this.selectedModuleIndex = idx;
                    if (this.searchBox != null) {
                        this.searchBox.setValue("");
                    }
                    this.searchQuery = "";
                    this.scrollOffset = 0.0;
                    init(this.width, this.height);
                },
                isSelected
            ));
            catY += catStep;
        }

        // Main Panel: Modern Pill Search Input (vertically centered at sbY + 7)
        int searchBoxW = Math.min(144, this.mainW - 130);
        this.searchBox = new EditBox(this.font, this.mainX + 26, this.mainY + 12, searchBoxW - 26, 12, uText("Search"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(uText("Suchen..."));
        this.searchBox.setTextColor(0xFFFFFFFF);
        this.searchBox.setTextColorUneditable(0xFF94A3B8);
        this.searchBox.setValue(this.searchQuery);
        this.searchBox.setResponder(query -> {
            this.searchQuery = query;
            this.scrollOffset = 0.0;
            rebuildSettingRows();
        });
        addRenderableWidget(this.searchBox);

        // Reset Button on top right of main panel with 12x12 reset icon
        Component resetText = uText(I18n.get("gm-addon.screen.settings.btn_reset"));
        int resetW = Math.max(82, this.font.width(resetText) + 28);
        addRenderableWidget(new ModernGlassButton(
            this.mainX + this.mainW - resetW - 6, this.mainY + 6, resetW, 20,
            resetText, ICON_RESET,
            btn -> {
                if (selectedModuleIndex < modules.size()) {
                    modules.get(selectedModuleIndex).resetDefaults(addon);
                    addon.saveConfig();
                    rebuildSettingRows();
                }
            },
            false
        ));

        rebuildSettingRows();
    }

    private void rebuildSettingRows() {
        for (SettingCardRow row : activeCards) {
            removeWidget(row.control());
        }
        activeCards.clear();

        List<SettingEntry> displayedSettings = getMatchingSettings();
        if (displayedSettings.isEmpty()) {
            this.maxScroll = 0.0;
            return;
        }

        int cols = (this.mainW >= 360) ? 2 : 1;
        int colGap = 6;
        int cardW = (this.mainW - 12 - (colGap * (cols - 1))) / cols;
        int cardH = 36;
        int rowGap = 6;

        for (int i = 0; i < displayedSettings.size(); i++) {
            SettingEntry entry = displayedSettings.get(i);
            int col = i % cols;
            int row = i / cols;
            int localY = row * (cardH + rowGap);
            int cardX = this.mainX + 6 + col * (cardW + colGap);

            AbstractWidget control = createControlForSetting(entry.setting(), cardX, cardW, cardH);
            if (control != null) {
                int controlW = control.getWidth();
                if (entry.setting() instanceof Setting.StringSetting) {
                    controlW += 12;
                }
                int maxLabelW = Math.max(36, cardW - controlW - 40);
                Component labelComp = uText(entry.setting().label());
                Identifier icon = getSettingIcon(entry.setting().id());

                addRenderableWidget(control);
                activeCards.add(new SettingCardRow(entry, control, icon, labelComp, maxLabelW, cardX, localY, cardW, cardH));
            }
        }

        int totalRows = (displayedSettings.size() + cols - 1) / cols;
        int totalContentHeight = totalRows * (cardH + rowGap);
        this.maxScroll = Math.max(0.0, totalContentHeight - this.gridH);
        this.scrollOffset = Math.clamp(this.scrollOffset, 0.0, this.maxScroll);

        updateRowPositions();
    }

    private List<SettingEntry> getMatchingSettings() {
        List<ClientFeatureModule> modules = addon.getModuleManager().getModules();
        List<SettingEntry> result = new ArrayList<>();

        if (searchQuery != null && !searchQuery.isBlank()) {
            String q = searchQuery.toLowerCase(Locale.ROOT).trim();
            for (ClientFeatureModule mod : modules) {
                for (Setting<?> s : mod.getSettings()) {
                    String label = s.label().toLowerCase(Locale.ROOT);
                    String desc = s.description().toLowerCase(Locale.ROOT);
                    if (label.contains(q) || desc.contains(q) || s.id().toLowerCase(Locale.ROOT).contains(q)) {
                        result.add(new SettingEntry(s, mod.getId(), mod.getDisplayName()));
                    }
                }
            }
        } else if (selectedModuleIndex < modules.size()) {
            ClientFeatureModule mod = modules.get(selectedModuleIndex);
            for (Setting<?> s : mod.getSettings()) {
                result.add(new SettingEntry(s, mod.getId(), mod.getDisplayName()));
            }
        }
        return result;
    }

    private AbstractWidget createControlForSetting(Setting<?> setting, int cardX, int cardW, int cardH) {
        if (setting instanceof Setting.BooleanSetting boolSetting) {
            int switchW = 34;
            int switchH = 18;
            int switchX = cardX + cardW - switchW - 8;
            return new AnimatedSwitchButton(switchX, this.gridY + 9, switchW, switchH, boolSetting.getter(), boolSetting.setter());
        } else if (setting instanceof Setting.IntSliderSetting intSetting) {
            int sliderW = Math.min(94, cardW / 2 - 4);
            int sliderH = 24;
            int sliderX = cardX + cardW - sliderW - 8;
            int cur = intSetting.getter().get();
            double norm = (double) (cur - intSetting.min()) / Math.max(1.0, (double) (intSetting.max() - intSetting.min()));
            return new ModernSliderButton(sliderX, this.gridY + 6, sliderW, sliderH,
                uText(cur + intSetting.unitSuffix()), Math.clamp(norm, 0.0, 1.0)) {
                @Override
                protected void updateMessage() {
                    int val = (int) Math.round(intSetting.min() + this.value * (intSetting.max() - intSetting.min()));
                    setMessage(uText(val + intSetting.unitSuffix()));
                }

                @Override
                protected void applyValue() {
                    int val = (int) Math.round(intSetting.min() + this.value * (intSetting.max() - intSetting.min()));
                    intSetting.setter().accept(val);
                }
            };
        } else if (setting instanceof Setting.DoubleSliderSetting dblSetting) {
            int sliderW = Math.min(94, cardW / 2 - 4);
            int sliderH = 24;
            int sliderX = cardX + cardW - sliderW - 8;
            double cur = dblSetting.getter().get();
            double norm = (cur - dblSetting.min()) / Math.max(0.0001, (dblSetting.max() - dblSetting.min()));
            return new ModernSliderButton(sliderX, this.gridY + 6, sliderW, sliderH,
                uText(formatDoubleValue(cur, dblSetting.step(), dblSetting.unitSuffix())), Math.clamp(norm, 0.0, 1.0)) {
                @Override
                protected void updateMessage() {
                    double val = snapStep(dblSetting.min() + this.value * (dblSetting.max() - dblSetting.min()), dblSetting.step());
                    setMessage(uText(formatDoubleValue(val, dblSetting.step(), dblSetting.unitSuffix())));
                }

                @Override
                protected void applyValue() {
                    double val = snapStep(dblSetting.min() + this.value * (dblSetting.max() - dblSetting.min()), dblSetting.step());
                    dblSetting.setter().accept(val);
                }
            };
        } else if (setting instanceof Setting.CycleSetting cycleSetting) {
            int maxOptW = 40;
            if (cycleSetting.options() != null) {
                for (String opt : cycleSetting.options()) {
                    maxOptW = Math.max(maxOptW, this.font.width(uText(getCycleOptionLabel(setting.id(), opt))));
                }
            }
            int cycleW = Math.clamp(maxOptW + 18, 54, Math.min(94, cardW / 2 - 4));
            int cycleH = 20;
            int cycleX = cardX + cardW - cycleW - 8;
            String initial = cycleSetting.getter().get();
            String initialLabel = getCycleOptionLabel(setting.id(), initial);
            return new ModernGlassButton(cycleX, this.gridY + 8, cycleW, cycleH, uText(initialLabel), null, btn -> {
                List<String> opts = cycleSetting.options();
                if (opts == null || opts.isEmpty()) return;
                String cur = cycleSetting.getter().get();
                int idx = opts.indexOf(cur);
                String next = opts.get((idx + 1) % opts.size());
                cycleSetting.setter().accept(next);
                String nextLabel = getCycleOptionLabel(setting.id(), next);
                btn.setMessage(uText(nextLabel));
            }, false);
        } else if (setting instanceof Setting.ActionSetting actionSetting) {
            Component btnLabel = uText(actionSetting.buttonText());
            int actW = Math.clamp(this.font.width(btnLabel) + 16, 62, Math.min(98, cardW / 2));
            int actH = 20;
            int actX = cardX + cardW - actW - 8;
            return new ModernGlassButton(actX, this.gridY + 8, actW, actH, btnLabel, null,
                btn -> actionSetting.action().run(), false);
        } else if (setting instanceof Setting.StringSetting strSetting) {
            int strW = Math.min(92, cardW / 2 - 4);
            int strX = cardX + cardW - strW - 8;
            EditBox box = new EditBox(this.font, strX + 6, this.gridY + 14, strW - 12, 12, uText(strSetting.label()));
            box.setBordered(false);
            box.setMaxLength(256);
            box.setTextColor(0xFFFFFFFF);
            box.setTextColorUneditable(0xFF94A3B8);
            String current = strSetting.getter().get();
            box.setValue(current != null ? current : "");
            box.setResponder(strSetting.setter());
            return box;
        }
        return null;
    }

    private void updateRowPositions() {
        int topClip = this.gridY;
        int bottomClip = this.gridY + this.gridH;

        for (SettingCardRow row : activeCards) {
            int ctrlH = row.control().getHeight();
            int offsetY = (row.cardH() - ctrlH) / 2;
            if (row.entry().setting() instanceof Setting.StringSetting) {
                offsetY = 14;
            }
            int screenY = (int) Math.round(this.gridY + row.baseLocalY() - scrollOffset + offsetY);
            row.control().setY(screenY);
            row.control().visible = (screenY >= topClip - 4 && screenY + ctrlH <= bottomClip + 4);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x66060911);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Main Window Body (94% opaque Modern dark slate-navy, 8x-supersampled 9-slice)
        drawNineSlice(graphics, TEX_WINDOW_BG, this.winX, this.winY, this.winW, this.winH, 8);

        // Window Header Bar: Logo & Version Badge
        Component brandTitle = uText("enhancedGM");
        graphics.text(this.font, brandTitle, this.winX + 14, this.winY + 13, 0xFF38BDF8, true);

        // Smooth HD Version Pill Badge
        String modVer = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("gm-addon")
            .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString())
            .orElse("v0.3.1");
        Component versionText = uText(modVer);
        int vTextW = this.font.width(versionText);
        int vBadgeW = vTextW + 14;
        int vBadgeX = this.winX + 20 + this.font.width(brandTitle);
        int vBadgeY = this.winY + 9;
        drawTexture(graphics, TEX_BADGE_BG, vBadgeX, vBadgeY, vBadgeW, 16);
        graphics.text(this.font, versionText, vBadgeX + (vBadgeW - vTextW) / 2, vBadgeY + 4, 0xFF7DD3FC, false);

        // Sidebar Card Container (90% opaque recessed dark slate)
        drawNineSlice(graphics, TEX_CONTAINER_BG, this.sideX, this.sideY, this.sideW, this.sideH, 8);
        graphics.text(this.font, uText("Kategorien"), this.sideX + 12, this.sideY + 8, 0xFF94A3B8, false);

        // Main Content Area Container (90% opaque recessed dark slate)
        drawNineSlice(graphics, TEX_CONTAINER_BG, this.mainX, this.mainY, this.mainW, this.mainH, 8);

        // Search Pill Box in Main Panel Header
        int sbX = this.mainX + 8;
        int sbY = this.mainY + 5;
        int sbW = Math.min(144, this.mainW - 130);
        int sbH = 22;
        Identifier searchTex = (this.searchBox != null && this.searchBox.isFocused()) ? TEX_INPUT_FOCUSED : TEX_INPUT_NORMAL;
        drawNineSlice(graphics, searchTex, sbX, sbY, sbW, sbH, 8);
        drawTexture(graphics, ICON_SEARCH, sbX + 7, sbY + 5, 12, 12);

        // Active Category / Search Title (fitted between search box and reset button)
        List<ClientFeatureModule> modules = addon.getModuleManager().getModules();
        String currentCatName = (selectedModuleIndex < modules.size()) ? modules.get(selectedModuleIndex).getDisplayName() : "";
        String headerTitle = (searchQuery != null && !searchQuery.isBlank())
            ? ("Suche: \"" + searchQuery + "\"")
            : currentCatName;
        int titleX = sbX + sbW + 12;
        int maxTitleW = this.mainX + this.mainW - 100 - titleX;
        drawFittedText(graphics, this.font, uText(headerTitle), titleX, this.mainY + 12, maxTitleW, 0xFFFFFFFF, true);

        // Top Header Separator Line
        graphics.horizontalLine(this.mainX + 6, this.mainX + this.mainW - 6, this.mainY + 31, 0x3338BDF8);

        // Reset hover detection
        this.hoveredEntry = null;

        // Scissored Card Grid
        graphics.enableScissor(this.mainX + 2, this.gridY, this.mainX + this.mainW - 2, this.gridY + this.gridH);
        for (SettingCardRow row : activeCards) {
            int cardY = (int) Math.round(this.gridY + row.baseLocalY() - scrollOffset);
            if (cardY + row.cardH() < this.gridY || cardY > this.gridY + this.gridH) continue;

            boolean isHovered = mouseX >= row.cardX() && mouseX <= row.cardX() + row.cardW()
                && mouseY >= cardY && mouseY <= cardY + row.cardH()
                && mouseY >= this.gridY && mouseY <= this.gridY + this.gridH;

            if (isHovered) {
                this.hoveredEntry = row.entry();
            }

            Identifier cardTex = isHovered ? TEX_CARD_HOVER : TEX_CARD_NORMAL;
            drawNineSlice(graphics, cardTex, row.cardX(), cardY, row.cardW(), row.cardH(), 8);

            // Left Flat 2D 128x128 HD Icon (bilinear-filtered)
            int iconX = row.cardX() + 8;
            int iconY = cardY + (row.cardH() - 16) / 2;
            drawTexture(graphics, row.icon(), iconX, iconY, 16, 16);

            // Auto-Fitted High-Contrast Calibri Bold Setting Label (never overflows or clips)
            int textColor = isHovered ? 0xFFFFFFFF : 0xFFF8FAFC;
            drawFittedText(graphics, this.font, row.formattedLabel(), row.cardX() + 28, cardY + (row.cardH() - 8) / 2, row.maxLabelW(), textColor, true);

            // For StringSetting EditBox, draw smooth rounded input frame
            if (row.entry().setting() instanceof Setting.StringSetting) {
                int bx = row.control().getX() - 6;
                int by = cardY + 8;
                int bw = row.control().getWidth() + 12;
                int bh = 20;
                drawNineSlice(graphics, row.control().isFocused() ? TEX_INPUT_FOCUSED : TEX_INPUT_NORMAL, bx, by, bw, bh, 8);
            }
        }
        graphics.disableScissor();

        // Bottom Detail Capsule Card
        int detailY = this.mainY + this.mainH - 34;
        drawNineSlice(graphics, TEX_CARD_NORMAL, this.mainX + 6, detailY, this.mainW - 12, 28, 8);

        if (this.hoveredEntry != null) {
            Identifier descIcon = getSettingIcon(this.hoveredEntry.setting().id());
            drawTexture(graphics, descIcon, this.mainX + 14, detailY + 6, 16, 16);
            int maxDetailW = this.mainW - 52;
            drawFittedText(graphics, this.font, uText(this.hoveredEntry.setting().label()), this.mainX + 34, detailY + 4, maxDetailW, 0xFF38BDF8, true);
            drawFittedText(graphics, this.font, uText(this.hoveredEntry.setting().description()), this.mainX + 34, detailY + 15, maxDetailW, 0xFFF1F5F9, false);
        } else {
            drawFittedText(graphics, this.font, uText("Fahre mit der Maus über eine Einstellung für Details & Hilfe."), this.mainX + 14, detailY + 10, this.mainW - 28, 0xFF94A3B8, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= this.mainX && mouseX <= this.mainX + this.mainW
            && mouseY >= this.gridY && mouseY <= this.gridY + this.gridH
            && maxScroll > 0.0 && scrollY != 0.0) {
            this.scrollOffset = Math.clamp(this.scrollOffset - scrollY * 20.0, 0.0, this.maxScroll);
            updateRowPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() == 0 && event.y() >= this.gridY && event.y() <= this.gridY + this.gridH) {
            for (SettingCardRow row : activeCards) {
                int cardY = (int) Math.round(this.gridY + row.baseLocalY() - scrollOffset);
                if (event.x() >= row.cardX() && event.x() <= row.cardX() + row.cardW()
                    && event.y() >= cardY && event.y() <= cardY + row.cardH()) {
                    if (row.entry().setting() instanceof Setting.BooleanSetting boolSetting) {
                        boolean next = !boolSetting.getter().get();
                        boolSetting.setter().accept(next);
                        return true;
                    }
                    break;
                }
            }
        }
        return false;
    }

    /**
     * Draws a full-quad texture with correct (x0, y0, x1, y1, u0, u1, v0, v1) coordinates.
     */
    public static void drawTexture(GuiGraphicsExtractor graphics, Identifier tex, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        graphics.blit(tex, x, y, x + w, y + h, 0.0f, 1.0f, 0.0f, 1.0f);
    }

    /**
     * Draws a full-quad texture with ARGB tint/alpha modulation.
     */
    public static void drawTextureTinted(GuiGraphicsExtractor graphics, Identifier tex, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        graphics.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0f, 0.0f, w, h, w, h, w, h, color);
    }

    /**
     * Draws an 8x-supersampled 256x256 9-slice rounded texture with zero pixel staircases.
     * Each 64x64 corner slice in the texture maps to cornerSize x cornerSize GUI units.
     */
    public static void drawNineSlice(GuiGraphicsExtractor graphics, Identifier tex, int x, int y, int w, int h, int cornerSize) {
        if (w <= 0 || h <= 0) return;
        int c = Math.min(cornerSize, Math.min(w / 2, h / 2));
        int x1 = x + c;
        int x2 = x + w - c;
        int x3 = x + w;
        int y1 = y + c;
        int y2 = y + h - c;
        int y3 = y + h;

        // Top row
        graphics.blit(tex, x,  y,  x1, y1, 0.0f,  0.25f, 0.0f,  0.25f);
        if (x2 > x1) {
            graphics.blit(tex, x1, y,  x2, y1, 0.25f, 0.75f, 0.0f,  0.25f);
        }
        graphics.blit(tex, x2, y,  x3, y1, 0.75f, 1.0f,  0.0f,  0.25f);

        // Middle row
        if (y2 > y1) {
            graphics.blit(tex, x,  y1, x1, y2, 0.0f,  0.25f, 0.25f, 0.75f);
            if (x2 > x1) {
                graphics.blit(tex, x1, y1, x2, y2, 0.25f, 0.75f, 0.25f, 0.75f);
            }
            graphics.blit(tex, x2, y1, x3, y2, 0.75f, 1.0f,  0.25f, 0.75f);
        }

        // Bottom row
        graphics.blit(tex, x,  y2, x1, y3, 0.0f,  0.25f, 0.75f, 1.0f);
        if (x2 > x1) {
            graphics.blit(tex, x1, y2, x2, y3, 0.25f, 0.75f, 0.75f, 1.0f);
        }
        graphics.blit(tex, x2, y2, x3, y3, 0.75f, 1.0f,  0.75f, 1.0f);
    }

    public static Identifier getCategoryIcon(String moduleId) {
        if (moduleId == null) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/general.png");
        return switch (moduleId.toLowerCase(Locale.ROOT)) {
            case "general" -> Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/general.png");
            case "blitzer" -> Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/blitzer.png");
            case "tacho" -> Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/tacho.png");
            case "minimap" -> Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/minimap.png");
            case "spawn_protection", "protection" -> Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/protection.png");
            case "plot", "plots" -> Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/plot.png");
            case "debug" -> Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/debug.png");
            default -> Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/general.png");
        };
    }

    public static Identifier getSettingIcon(String settingId) {
        if (settingId == null) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/general.png");
        String s = settingId.toLowerCase(Locale.ROOT);
        if (s.contains("language")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/language.png");
        if (s.contains("server") || s.contains("domain")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/general.png");
        if (s.contains("update")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/update.png");
        if (s.contains("reset")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/reset.png");
        if (s.contains("cloud") || s.contains("firebase")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/update.png");
        if (s.contains("learn")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/language.png");
        if (s.contains("vehicle")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/car.png");
        if (s.contains("sound") || s.contains("audio")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/bell.png");
        if (s.contains("3d") || s.contains("marker")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/marker.png");
        if (s.contains("distance") || s.contains("radius")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/scale.png");
        if (s.contains("scale") || s.contains("size")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/scale.png");
        if (s.contains("tacho") || s.contains("speed")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/tacho.png");
        if (s.contains("motor") || s.contains("engine")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/car.png");
        if (s.contains("tempomat")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/tacho.png");
        if (s.contains("minimap") || s.contains("map")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/minimap.png");
        if (s.contains("coord")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/marker.png");
        if (s.contains("shape")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/marker.png");
        if (s.contains("spawn") || s.contains("protection")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/protection.png");
        if (s.contains("plot") || s.contains("border")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/plot.png");
        if (s.contains("toast") || s.contains("alert")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/bell.png");
        if (s.contains("debug") || s.contains("log")) return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/debug.png");
        return Identifier.fromNamespaceAndPath("gm-addon", "textures/gui/icons/general.png");
    }

    private static String getCycleOptionLabel(String settingId, String option) {
        if (option == null) return "";
        String key = "gm-addon.cycle." + settingId + "." + option.toLowerCase(Locale.ROOT);
        return I18n.has(key) ? I18n.get(key) : option;
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
        de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat.setScreen(Minecraft.getInstance(), parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Smooth 8x-Supersampled 9-Slice Pill Button with optional 12x12 HD icon and auto-fitted typography.
     */
    public static class ModernGlassButton extends Button {
        private final Identifier icon;
        private boolean isAccent;

        public ModernGlassButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean isAccent) {
            this(x, y, width, height, message, null, onPress, isAccent);
        }

        public ModernGlassButton(int x, int y, int width, int height, Component message, Identifier icon, OnPress onPress, boolean isAccent) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.icon = icon;
            this.isAccent = isAccent;
        }

        public void setAccent(boolean accent) {
            this.isAccent = accent;
        }

        @Override
        public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int px = getX();
            int py = getY();
            int pw = getWidth();
            int ph = getHeight();
            boolean hovered = isHovered();

            Identifier tex = isAccent
                ? (hovered ? TEX_BTN_ACCENT_HOVER : TEX_BTN_ACCENT)
                : (hovered ? TEX_BTN_SECONDARY_HOVER : TEX_BTN_SECONDARY);

            drawNineSlice(graphics, tex, px, py, pw, ph, 8);

            Font font = Minecraft.getInstance().font;
            Component msg = getMessage();
            int textY = py + (ph - 8) / 2;
            int textColor = hovered ? 0xFFFFFFFF : (isAccent ? 0xFFFFFFFF : 0xFFF1F5F9);

            if (this.icon != null) {
                int iconSize = 12;
                int textW = font.width(msg);
                int maxTextW = Math.max(16, pw - iconSize - 14);
                int actualTextW = Math.min(textW, maxTextW);
                int totalW = iconSize + 4 + actualTextW;
                int startX = px + Math.max(5, (pw - totalW) / 2);
                drawTexture(graphics, this.icon, startX, py + (ph - iconSize) / 2, iconSize, iconSize);
                drawFittedText(graphics, font, msg, startX + iconSize + 4, textY, maxTextW, textColor, true);
            } else {
                drawCenteredFittedText(graphics, font, msg, px + pw / 2, textY, pw - 10, textColor, true);
            }
        }
    }

    /**
     * Sleek HD Close Button using 9-slice danger pill and 128x128 bilinear-filtered close icon.
     */
    public static class CloseButton extends Button {
        public CloseButton(int x, int y, int width, int height, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        }

        @Override
        public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int px = getX();
            int py = getY();
            int pw = getWidth();
            int ph = getHeight();
            boolean hovered = isHovered();

            drawNineSlice(graphics, hovered ? TEX_BTN_DANGER_HOVER : TEX_BTN_DANGER, px, py, pw, ph, 8);
            drawTexture(graphics, ICON_CLOSE, px + (pw - 12) / 2, py + (ph - 12) / 2, 12, 12);
        }
    }

    /**
     * Sidebar Category Pill Button with 128x128 HD Icon, 8x-supersampled 9-slice surface, and auto-fitted text.
     */
    public static class ModernCategoryButton extends Button {
        private final Identifier icon;
        private final boolean selected;

        public ModernCategoryButton(int x, int y, int width, int height, Component message, String moduleId, OnPress onPress, boolean selected) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.icon = getCategoryIcon(moduleId);
            this.selected = selected;
        }

        @Override
        public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int px = getX();
            int py = getY();
            int pw = getWidth();
            int ph = getHeight();
            boolean hovered = isHovered();

            Identifier tex = selected ? TEX_CAT_ACTIVE : (hovered ? TEX_CAT_HOVER : TEX_CAT_NORMAL);
            drawNineSlice(graphics, tex, px, py, pw, ph, 8);

            // Flat 2D 128x128 HD Icon
            int iconSize = 14;
            int iconX = px + 7;
            int iconY = py + (ph - iconSize) / 2;
            drawTexture(graphics, this.icon, iconX, iconY, iconSize, iconSize);

            Component msg = getMessage();
            int textColor = selected ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : 0xFFE2E8F0);
            int textX = px + 25;
            int textY = py + (ph - 8) / 2;
            int maxTextW = pw - 31;
            drawFittedText(graphics, Minecraft.getInstance().font, msg, textX, textY, maxTextW, textColor, true);
        }
    }

    /**
     * Animated Modern -style Pill Switch Button using 8x-supersampled bilinear-filtered textures.
     */
    public static class AnimatedSwitchButton extends Button {
        private final java.util.function.Supplier<Boolean> getter;
        private final java.util.function.Consumer<Boolean> setter;
        private float animationProgress = -1.0f;
        private long lastTimeMs = 0L;

        public AnimatedSwitchButton(int x, int y, int width, int height, java.util.function.Supplier<Boolean> getter, java.util.function.Consumer<Boolean> setter) {
            super(x, y, width, height, Component.empty(), btn -> {
                boolean next = !getter.get();
                setter.accept(next);
            }, DEFAULT_NARRATION);
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean targetOn = getter.get();
            float targetValue = targetOn ? 1.0f : 0.0f;

            if (animationProgress < 0.0f) {
                animationProgress = targetValue;
            }

            long now = System.currentTimeMillis();
            if (lastTimeMs == 0L) lastTimeMs = now;
            float dt = Math.clamp((now - lastTimeMs) / 1000.0f, 0.001f, 0.05f);
            lastTimeMs = now;

            // Smooth spring ease-out
            animationProgress += (targetValue - animationProgress) * Math.min(1.0f, dt * 22.0f);
            if (Math.abs(targetValue - animationProgress) < 0.005f) {
                animationProgress = targetValue;
            }

            int px = getX();
            int py = getY();
            int pw = getWidth();
            int ph = getHeight();

            // Smoothly cross-fade between 8x-supersampled OFF and ON pill tracks
            if (animationProgress <= 0.01f) {
                drawTexture(graphics, TEX_SWITCH_OFF, px, py, pw, ph);
            } else if (animationProgress >= 0.99f) {
                drawTexture(graphics, TEX_SWITCH_ON, px, py, pw, ph);
            } else {
                drawTexture(graphics, TEX_SWITCH_OFF, px, py, pw, ph);
                int alpha = Math.clamp((int) Math.round(animationProgress * 255.0f), 0, 255);
                drawTextureTinted(graphics, TEX_SWITCH_ON, px, py, pw, ph, (alpha << 24) | 0xFFFFFF);
            }

            // Smooth Sliding Circular White Knob with Gaussian Drop Shadow
            int knobSize = ph - 2;
            int knobMinX = px + 1;
            int knobMaxX = px + pw - knobSize - 1;
            int knobX = (int) Math.round(knobMinX + (knobMaxX - knobMinX) * animationProgress);
            int knobY = py + 1;
            drawTexture(graphics, TEX_SWITCH_KNOB, knobX, knobY, knobSize, knobSize);
        }
    }

    /**
     * Smooth 8x-Supersampled Modern Slider with unobstructed value display above a slim 5px rail
     * and a sky-blue-ringed circular HD thumb.
     */
    public static abstract class ModernSliderButton extends AbstractSliderButton {
        public ModernSliderButton(int x, int y, int width, int height, Component message, double value) {
            super(x, y, width, height, message, value);
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int px = getX();
            int py = getY();
            int pw = getWidth();
            boolean hovered = isHovered();

            // 1. Unobstructed Value Text on top (never overlapped by the slider thumb)
            int textColor = hovered ? 0xFF38BDF8 : 0xFFF8FAFC;
            drawCenteredFittedText(graphics, Minecraft.getInstance().font, getMessage(), px + pw / 2, py + 1, pw - 4, textColor, true);

            // 2. Slim 5px Horizontal Pill Rail at the bottom
            int railX = px + 2;
            int railY = py + 15;
            int railW = pw - 4;
            int railH = 5;
            drawNineSlice(graphics, TEX_SLIDER_RAIL_BG, railX, railY, railW, railH, 2);

            // 3. Thumb Center X & Aligned 5px Blue Fill Bar (terminates right inside the thumb center)
            int thumbCenterX = px + 6 + (int) Math.round((pw - 12) * this.value);
            int fillW = thumbCenterX - railX;
            if (fillW > 2) {
                drawNineSlice(graphics, TEX_SLIDER_RAIL_FILL, railX, railY, fillW, railH, 2);
            }

            // 4. Crisp 128x128 Sky-Blue-Ringed Circular Thumb riding symmetrically on the 5px rail
            int thumbSize = hovered ? 13 : 11;
            int thumbX = thumbCenterX - thumbSize / 2;
            int thumbY = railY + 2 - thumbSize / 2;
            drawTexture(graphics, hovered ? TEX_SLIDER_THUMB_HOVER : TEX_SLIDER_THUMB, thumbX, thumbY, thumbSize, thumbSize);
        }
    }
}
