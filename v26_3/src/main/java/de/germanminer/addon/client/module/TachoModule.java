package de.germanminer.addon.client.module;

import de.germanminer.addon.client.GMAddonClient;
import de.germanminer.addon.config.AddonConfig;
import de.germanminer.addon.module.HudWidgetDescriptor;
import de.germanminer.addon.module.Setting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modular Speedometer ("Tacho") & Cruise Control ("Tempomat") HUD module.
 * Supports two visual styles: ANALOG (circular dial with rotating needle) and DIGITAL (modern dashboard card).
 */
public class TachoModule implements ClientFeatureModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("GM-Addon/Tacho");
    private static final Pattern NUMBER_KMH_PATTERN = Pattern.compile("(\\d{2,3})\\s*(?:km/h)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_STRIP_PATTERN = Pattern.compile("(?i)§[0-9A-FK-ORX]");

    private final GMAddonClient addon;
    private final HudWidgetDescriptor widgetDescriptor;
    private Field bossEventsField;
    private boolean bossFieldResolved = false;
    private Field hoveredSlotField;
    private boolean hoveredSlotFieldResolved = false;

    private String lastLoggedBossBar = "";
    private int steadyCruiseTicks = 0;
    private int lastSteadyTargetTenKmh = -1;

    public TachoModule(GMAddonClient addon) {
        this.addon = addon;
        this.widgetDescriptor = new HudWidgetDescriptor() {
            @Override
            public String getWidgetId() {
                return "tacho";
            }

            @Override
            public String getDisplayName() {
                return "Tacho & Tempomat";
            }

            @Override
            public boolean isEnabled() {
                return addon.getConfig().tachoEnabled;
            }

            @Override
            public void setEnabled(boolean enabled) {
                addon.getConfig().tachoEnabled = enabled;
                addon.saveConfig();
            }

            @Override
            public int getX() {
                return addon.getConfig().tachoX;
            }

            @Override
            public int getY() {
                return addon.getConfig().tachoY;
            }

            @Override
            public void setPosition(int x, int y) {
                addon.getConfig().tachoX = x;
                addon.getConfig().tachoY = y;
            }

            @Override
            public float getScale() {
                return addon.getConfig().tachoScale;
            }

            @Override
            public void setScale(float scale) {
                addon.getConfig().tachoScale = scale;
            }

            @Override
            public int getBaseWidth() {
                return "DIGITAL".equalsIgnoreCase(addon.getConfig().tachoStyle) ? 136 : 112;
            }

            @Override
            public int getBaseHeight() {
                return "DIGITAL".equalsIgnoreCase(addon.getConfig().tachoStyle) ? 62 : 112;
            }

            @Override
            public void resetToDefault() {
                AddonConfig cfg = addon.getConfig();
                cfg.tachoX = 330;
                cfg.tachoY = 230;
                cfg.tachoScale = 1.0f;
            }
        };
    }

    @Override
    public void onInit(GMAddonClient addon) {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                ScreenMouseEvents.beforeMouseClick(screen).register((scr, click) -> {
                    onContainerScreenClick(addon, containerScreen);
                });
            }
        });
    }

    private void onContainerScreenClick(GMAddonClient addon, AbstractContainerScreen<?> containerScreen) {
        try {
            Slot hovered = getHoveredSlot(containerScreen);
            if (hovered == null || !hovered.hasItem()) {
                return;
            }
            ItemStack stack = hovered.getItem();
            String combined = extractItemText(stack);
            String clean = COLOR_STRIP_PATTERN.matcher(combined).replaceAll("").trim();
            String lower = clean.toLowerCase(Locale.ROOT);
            if (!lower.contains("tempomat")) {
                return;
            }
            LOGGER.info("[VehicleMenu Click] Slot {}: {}", hovered.index, clean);

            if (lower.contains("erhöh") || lower.contains("+10") || lower.contains("plus") || lower.contains("+ 10")) {
                addon.getBlitzerTracker().onTempomatPlusCommand();
            } else if (lower.contains("reduzier") || lower.contains("-10") || lower.contains("minus") || lower.contains("- 10") || lower.contains("verringer")) {
                addon.getBlitzerTracker().onTempomatMinusCommand();
            } else if (lower.contains("aktivier") || lower.contains("deaktivier") || lower.contains("ein/aus") || lower.contains("an/aus") || lower.startsWith("tempomat")) {
                addon.getBlitzerTracker().onTempomatToggleCommand();
            }
        } catch (Throwable ignored) {
        }
    }

    private Slot getHoveredSlot(AbstractContainerScreen<?> screen) {
        try {
            if (!hoveredSlotFieldResolved) {
                hoveredSlotFieldResolved = true;
                for (Field f : AbstractContainerScreen.class.getDeclaredFields()) {
                    if (Slot.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        hoveredSlotField = f;
                        break;
                    }
                }
            }
            if (hoveredSlotField != null) {
                return (Slot) hoveredSlotField.get(screen);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public String getId() {
        return "tacho";
    }

    @Override
    public String getDisplayName() {
        return "Tacho & Tempomat";
    }

    @Override
    public String getDescription() {
        return "Geschwindigkeitsanzeige (Analog & Digital) mit Tempomat-Anzeige und Tempomat-Hotkeys.";
    }

    @Override
    public boolean isEnabled() {
        return addon.getConfig().tachoEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        addon.getConfig().tachoEnabled = enabled;
        addon.saveConfig();
    }

    @Override
    public Optional<HudWidgetDescriptor> getHudWidget() {
        return Optional.of(widgetDescriptor);
    }

    @Override
    public void onChatMessage(GMAddonClient addon, String plainMessage) {
        addon.getBlitzerTracker().onChatMessageForTempomat(plainMessage);
    }

    @Override
    public void onActionBarMessage(GMAddonClient addon, String plainMessage) {
        addon.getBlitzerTracker().onActionBarMessage(plainMessage);
    }

    @Override
    public void onClientTick(GMAddonClient addon, Minecraft client) {
        if (client.player == null || client.gui == null) {
            return;
        }
        boolean inVehicle = client.player.isPassenger() || client.player.getVehicle() != null;
        if (inVehicle) {
            inspectBossBarsForTempomat(addon, client);
            inspectHotbarForTempomat(addon, client);
            checkSteadyCruiseAutoSync(addon, client);
        } else {
            steadyCruiseTicks = 0;
            lastSteadyTargetTenKmh = -1;
        }
        // Always inspect open server container menus (e.g. /vehicles menu) to stay 100% in sync
        if (client.gui.screen() instanceof AbstractContainerScreen<?> containerScreen) {
            inspectContainerScreenForTempomat(addon, containerScreen);
        }
    }

    /**
     * When the player holds forward throttle ('W') in a vehicle with Tempomat active and
     * the server caps the vehicle at a steady multiple of 10 km/h (e.g., 50, 60, 70 km/h)
     * for ~1.2 seconds, automatically self-heals any out-of-sync Tempomat speed on the HUD.
     */
    private void checkSteadyCruiseAutoSync(GMAddonClient addon, Minecraft client) {
        var tracker = addon.getBlitzerTracker();
        if (!tracker.isTempomatActive() || addon.hasPendingTempomatCommands()) {
            steadyCruiseTicks = 0;
            return;
        }
        if (!client.options.keyUp.isDown() || client.options.keyDown.isDown()) {
            steadyCruiseTicks = 0;
            return;
        }
        double speed = tracker.getCurrentSpeedKmh();
        if (speed < 18.0) {
            steadyCruiseTicks = 0;
            return;
        }
        int nearestTen = (int) Math.round(speed / 10.0) * 10;
        if (Math.abs(speed - nearestTen) <= 1.8 && nearestTen >= 20 && nearestTen <= 250) {
            if (nearestTen == lastSteadyTargetTenKmh) {
                steadyCruiseTicks++;
                if (steadyCruiseTicks >= 24 && tracker.getTempomatSpeedKmh() != nearestTen) {
                    tracker.syncTempomatFromServerUi(true, nearestTen);
                    steadyCruiseTicks = 0;
                }
            } else {
                lastSteadyTargetTenKmh = nearestTen;
                steadyCruiseTicks = 1;
            }
        } else {
            steadyCruiseTicks = 0;
        }
    }

    /**
     * Scans all items in an open server GUI menu (such as GermanMiner's vehicle menu)
     * so changes to the Tempomat made via the server menu immediately sync with the HUD.
     */
    private void inspectContainerScreenForTempomat(GMAddonClient addon, AbstractContainerScreen<?> screen) {
        try {
            var menu = screen.getMenu();
            if (menu == null || menu.slots == null) return;
            for (Slot slot : menu.slots) {
                if (slot != null && slot.hasItem()) {
                    inspectItemStackForTempomat(addon, slot.getItem());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void inspectHotbarForTempomat(GMAddonClient addon, Minecraft client) {
        try {
            var inv = client.player.getInventory();
            for (int i = 0; i < 9; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack != null && !stack.isEmpty()) {
                    inspectItemStackForTempomat(addon, stack);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private String extractItemText(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        StringBuilder fullText = new StringBuilder(stack.getHoverName().getString());
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && lore.lines() != null) {
            for (Component line : lore.lines()) {
                fullText.append(" | ").append(line.getString());
            }
        }
        return fullText.toString();
    }

    private void inspectItemStackForTempomat(GMAddonClient addon, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        String rawCombined = extractItemText(stack);
        String clean = COLOR_STRIP_PATTERN.matcher(rawCombined).replaceAll("").trim();
        String lower = clean.toLowerCase(Locale.ROOT);
        if (!lower.contains("tempomat")) {
            return;
        }

        // Ignore generic "+10" or "-10" adjustment buttons in the menu unless they state the current Tempomat speed
        boolean isPlusMinusButton = (lower.contains("+") || lower.contains("-") || lower.contains("erhöh") || lower.contains("reduzier"))
            && !lower.contains("aktuell") && !lower.contains("geschwindigkeit:") && !lower.contains("status");

        Boolean detectedActive = null;
        if (lower.contains("deaktiviert") || lower.contains("ausgeschaltet") || lower.contains("status: aus")
            || lower.contains("(aus)") || lower.contains("inaktiv")) {
            detectedActive = false;
        } else if (lower.contains("aktiviert") || lower.contains("eingeschaltet") || lower.contains("status: an")
            || lower.contains("(an)") || lower.contains("status: aktiv")) {
            detectedActive = true;
        }

        Integer detectedSpeed = null;
        if (!isPlusMinusButton) {
            Matcher m = NUMBER_KMH_PATTERN.matcher(clean);
            while (m.find()) {
                try {
                    int val = Integer.parseInt(m.group(1));
                    if (val >= 10 && val <= 260 && val % 5 == 0 && val != 10) {
                        detectedSpeed = val;
                        break;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (detectedSpeed == null && stack.getCount() >= 10 && stack.getCount() <= 250 && stack.getCount() % 10 == 0) {
                detectedSpeed = stack.getCount();
            }
        }

        if (detectedActive != null || detectedSpeed != null) {
            addon.getBlitzerTracker().syncTempomatFromServerUi(detectedActive, detectedSpeed);
        }
    }

    @SuppressWarnings("unchecked")
    private void inspectBossBarsForTempomat(GMAddonClient addon, Minecraft client) {
        try {
            if (!bossFieldResolved) {
                bossFieldResolved = true;
                for (Field f : BossHealthOverlay.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        bossEventsField = f;
                        break;
                    }
                }
            }
            if (bossEventsField != null) {
                BossHealthOverlay overlay = client.gui.hud.getBossOverlay();
                Object rawMap = bossEventsField.get(overlay);
                if (rawMap instanceof Map<?, ?> map) {
                    for (Object val : map.values()) {
                        if (val instanceof BossEvent event && event.getName() != null) {
                            String text = event.getName().getString();
                            if (text != null && !text.isBlank()) {
                                if (!text.equals(lastLoggedBossBar)) {
                                    lastLoggedBossBar = text;
                                    LOGGER.info("[Vehicle BossBar] '{}'", text);
                                }
                                addon.getBlitzerTracker().onChatMessageForTempomat(text);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void renderHudWidget(GMAddonClient addon, GuiGraphicsExtractor graphics, Minecraft mc, float partialTick, boolean editorPreview) {
        addon.getHudRenderer().renderTachoWidget(graphics, mc, partialTick, editorPreview);
    }

    @Override
    public List<Setting<?>> getSettings() {
        AddonConfig cfg = addon.getConfig();
        return List.of(
            new Setting.BooleanSetting(
                "tacho_enabled",
                "Tacho HUD aktivieren",
                "Zeigt den Tacho mit aktueller km/h und Tempomat-Status auf dem Bildschirm an.",
                () -> cfg.tachoEnabled,
                val -> {
                    cfg.tachoEnabled = val;
                    addon.saveConfig();
                }
            ),
            new Setting.CycleSetting(
                "tacho_style",
                "Tacho-Design (Typ)",
                "Wähle zwischen ANALOG (Rundinstrument mit Nadel) und DIGITAL (Modernes Dashboard).",
                List.of("ANALOG", "DIGITAL"),
                () -> cfg.tachoStyle != null ? cfg.tachoStyle.toUpperCase() : "ANALOG",
                val -> {
                    cfg.tachoStyle = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "tacho_only_vehicle",
                "Nur im Fahrzeug anzeigen",
                "Blendet den Tacho automatisch aus, wenn du nicht in einem Fahrzeug sitzt.",
                () -> cfg.tachoOnlyInVehicle,
                val -> {
                    cfg.tachoOnlyInVehicle = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "speed_calibration",
                "km/h Kalibrierungs-Faktor",
                "Kalibriert die Fahrzeug-Geschwindigkeit auf GermanMiner (Standard: 2.70 = exakte 50 km/h).",
                1.80, 3.60, 0.05, "x",
                () -> cfg.speedCalibrationFactor,
                val -> {
                    cfg.speedCalibrationFactor = val;
                    addon.saveConfig();
                }
            ),
            new Setting.IntSliderSetting(
                "tacho_max_kmh",
                "Maximalwert Tacho-Skala",
                "Höchstgeschwindigkeit für die Tacho-Nadel bzw. den Fortschrittsbalken.",
                100, 260, " km/h",
                () -> cfg.tachoMaxKmh,
                val -> {
                    cfg.tachoMaxKmh = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "tacho_scale",
                "Tacho Skalierung",
                "Größe des Tacho-Widgets im HUD.",
                0.6, 2.0, 0.05, "x",
                () -> (double) cfg.tachoScale,
                val -> {
                    cfg.tachoScale = val.floatValue();
                    addon.saveConfig();
                }
            )
        );
    }
}
