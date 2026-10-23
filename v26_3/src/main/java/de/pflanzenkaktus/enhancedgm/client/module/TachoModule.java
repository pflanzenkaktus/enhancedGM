package de.pflanzenkaktus.enhancedgm.client.module;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.util.I18n;
import de.pflanzenkaktus.enhancedgm.config.AddonConfig;
import de.pflanzenkaktus.enhancedgm.module.HudWidgetDescriptor;
import de.pflanzenkaktus.enhancedgm.module.Setting;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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
    private int idleCruiseTicks = 0;
    private double speedAtIdleStart = 0.0;
    private boolean telemetryConfirmedCruise = false;
    private volatile boolean vehicleBossBarAvailable = false;
    private volatile long lastVehicleBossBarTimestampMs = 0L;

    public TachoModule(GMAddonClient addon) {
        this.addon = addon;
        this.widgetDescriptor = new HudWidgetDescriptor() {
            @Override
            public String getWidgetId() {
                return "tacho";
            }

            @Override
            public String getDisplayName() {
                return I18n.get("gm-addon.widget.tacho.name");
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
        return I18n.get("gm-addon.module.tacho.name");
    }

    @Override
    public String getDescription() {
        return I18n.get("gm-addon.module.tacho.desc");
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

    public boolean isVehicleBossBarAvailable() {
        return vehicleBossBarAvailable || (System.currentTimeMillis() - lastVehicleBossBarTimestampMs <= 1000L);
    }

    public static boolean isVehicleBossBarText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String clean = COLOR_STRIP_PATTERN.matcher(text).replaceAll("").toLowerCase(java.util.Locale.ROOT).trim();
        return clean.contains("km/h")
            || clean.contains("gang:")
            || clean.contains("höhe:")
            || clean.contains("hoehe:")
            || clean.contains("motor")
            || clean.contains("tank")
            || clean.contains("fahrzeug")
            || clean.contains("geschwindigkeit");
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
        inspectBossBarsForTempomat(addon, client);
        boolean inVehicle = GMAddonClient.isPlayerInActualVehicle(client.player);
        if (inVehicle) {
            inspectVehicleEntityForTempomatCapability(addon, client);
            inspectHotbarForTempomat(addon, client);
            checkSteadyCruiseAutoSync(addon, client);
            checkAutonomousCruiseTelemetry(addon, client);
        } else {
            steadyCruiseTicks = 0;
            lastSteadyTargetTenKmh = -1;
            idleCruiseTicks = 0;
            speedAtIdleStart = 0.0;
            telemetryConfirmedCruise = false;
        }
        // Always inspect open server container menus (e.g. /vehicles menu) to stay 100% in sync
        if (de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat.getScreen(client) instanceof AbstractContainerScreen<?> containerScreen) {
            inspectContainerScreenForTempomat(addon, containerScreen);
        }
    }

    /**
     * Inspects the vehicle entity, vehicle type, and any equipped custom 3D model items (e.g. armor stand helmet)
     * to detect whether the vehicle supports cruise control (motorized cars/trucks/bikes vs bicycles, skateboards, wheelchairs).
     */
    private void inspectVehicleEntityForTempomatCapability(GMAddonClient addon, Minecraft client) {
        try {
            var tracker = addon.getBlitzerTracker();
            var vehicle = client.player.getVehicle();
            var root = client.player.getRootVehicle();
            Entity target = root != null ? root : vehicle;
            if (target == null) return;

            // Check non-motor entity type (living mounts)
            String typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString().toLowerCase(Locale.ROOT);
            if (typeId.contains("horse") || typeId.contains("donkey") || typeId.contains("mule")
                || typeId.contains("pig") || typeId.contains("strider") || typeId.contains("camel")) {
                tracker.setTempomatAvailable(false);
                return;
            }

            // Check custom vehicle entity names
            if (isUnsupportedVehicleName(target.getName().getString())
                || (target.getCustomName() != null && isUnsupportedVehicleName(target.getCustomName().getString()))) {
                tracker.setTempomatAvailable(false);
                return;
            }

            // Check LivingEntity equipment across all slots (ArmorStand helmet/chest/legs/feet/hands)
            if (target instanceof LivingEntity living) {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    ItemStack item = living.getItemBySlot(slot);
                    if (item != null && !item.isEmpty() && isUnsupportedVehicleName(extractItemText(item))) {
                        tracker.setTempomatAvailable(false);
                        return;
                    }
                }
            }

            // Also inspect passenger entities of the vehicle assembly
            if (root != null) {
                for (Entity passenger : root.getPassengers()) {
                    if (passenger != null && passenger != client.player) {
                        if (passenger.getCustomName() != null && isUnsupportedVehicleName(passenger.getCustomName().getString())) {
                            tracker.setTempomatAvailable(false);
                            return;
                        }
                        if (passenger instanceof LivingEntity livingPass) {
                            for (EquipmentSlot slot : EquipmentSlot.values()) {
                                ItemStack item = livingPass.getItemBySlot(slot);
                                if (item != null && !item.isEmpty() && isUnsupportedVehicleName(extractItemText(item))) {
                                    tracker.setTempomatAvailable(false);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isUnsupportedVehicleName(String name) {
        if (name == null || name.isBlank()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("fahrrad")
            || lower.contains("bike")
            || lower.contains("bmx")
            || lower.contains("mountainbike")
            || lower.contains("rennrad")
            || lower.contains("citybike")
            || lower.contains("drahtesel")
            || lower.contains("skateboard")
            || lower.contains("longboard")
            || lower.contains("tretroller")
            || lower.contains("rollstuhl")
            || lower.contains("wheelchair")
            || lower.contains("e-scooter")
            || (lower.contains("scooter") && !lower.contains("motor"))
            || (lower.contains("roller") && !lower.contains("motorroller"));
    }

    /**
     * Autonomous cruise telemetry monitor:
     * When Tempomat is active and the player is pressing nothing (no throttle 'W' and no brake 'S'):
     * 1. If the vehicle gets faster on its own (speed increases without throttle) or holds cruise speed,
     *    autonomous cruise is confirmed active and supported.
     * 2. If the vehicle instead rapidly decelerates / coasts to a halt under friction,
     *    Tempomat is not active/supported on this vehicle and is automatically switched to disabled.
     */
    private void checkAutonomousCruiseTelemetry(GMAddonClient addon, Minecraft client) {
        var tracker = addon.getBlitzerTracker();
        if (!tracker.isTempomatActive() || addon.hasPendingTempomatCommands()) {
            idleCruiseTicks = 0;
            return;
        }

        // Check if player is pressing nothing (no throttle 'W', no brake 'S')
        boolean pressingNothing = !client.options.keyUp.isDown() && !client.options.keyDown.isDown();
        if (!pressingNothing) {
            idleCruiseTicks = 0;
            speedAtIdleStart = tracker.getCurrentSpeedKmh();
            return;
        }

        double currentSpeed = tracker.getCurrentSpeedKmh();
        double targetSpeed = tracker.getTempomatSpeedKmh();

        idleCruiseTicks++;
        if (idleCruiseTicks == 1) {
            speedAtIdleStart = currentSpeed;
            return;
        }

        // Rule 1: Vehicle gets faster even when pressing nothing! (Autonomous acceleration)
        if (currentSpeed > (speedAtIdleStart + 1.2) && currentSpeed > 3.0) {
            telemetryConfirmedCruise = true;
            tracker.setTempomatAvailable(true);
            return;
        }

        // Rule 2: Vehicle actively maintains cruise speed around target for over 20 ticks (~1.0s)
        if (currentSpeed >= 12.0 && Math.abs(currentSpeed - targetSpeed) <= 3.5) {
            if (idleCruiseTicks >= 20) {
                telemetryConfirmedCruise = true;
                tracker.setTempomatAvailable(true);
            }
            return;
        }

        // Rule 3: Deceleration & friction coast-down check (Vehicle is NOT cruising)
        // If cruise was not confirmed, the vehicle was in motion (>= 10 km/h), and speed has dropped significantly
        // (> 6 km/h drop over 35 ticks, falling well below target), cruise control is not driving this vehicle.
        if (!telemetryConfirmedCruise && speedAtIdleStart >= 10.0) {
            if (idleCruiseTicks >= 35 && currentSpeed < (speedAtIdleStart - 6.0) && currentSpeed < (targetSpeed - 5.0)) {
                LOGGER.info("[Cruise Telemetry] Vehicle decelerated from {:.1f} to {:.1f} km/h with no throttle. Cruise control unavailable/inactive.",
                    speedAtIdleStart, currentSpeed);
                tracker.setTempomatActive(false);
                tracker.setTempomatAvailable(false);
                idleCruiseTicks = 0;
            }
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
        fullText.append(" | ").append(stack.getItem().toString());
        try {
            fullText.append(" | ").append(stack.getComponents().toString());
        } catch (Throwable ignored) {}
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
        if (client.player == null || client.player.getVehicle() == null) {
            this.vehicleBossBarAvailable = false;
            this.lastVehicleBossBarTimestampMs = 0L;
            return;
        }

        boolean foundVehicleBar = false;
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
                BossHealthOverlay overlay = de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat.getBossOverlay(client);
                if (overlay == null) {
                    this.vehicleBossBarAvailable = false;
                    return;
                }
                Object rawMap = bossEventsField.get(overlay);
                if (rawMap instanceof Map<?, ?> map) {
                    for (Object val : map.values()) {
                        if (val instanceof BossEvent event && event.getName() != null) {
                            String text = event.getName().getString();
                            if (text != null && !text.isBlank()) {
                                if (isVehicleBossBarText(text)) {
                                    foundVehicleBar = true;
                                    this.lastVehicleBossBarTimestampMs = System.currentTimeMillis();
                                }
                                float progress = event.getProgress();
                                if (!text.equals(lastLoggedBossBar)) {
                                    lastLoggedBossBar = text;
                                    LOGGER.info("[Vehicle BossBar] '{}' | Tank/Progress: {:.3f}", text, progress);
                                }
                                addon.getBlitzerTracker().onChatMessageForTempomat(text);
                                addon.getBlitzerTracker().onActionBarMessage(text);

                                // GermanMiner Fuel Tank & Vehicle Tempomat detection:
                                // Vehicle BossBars display driving speed ("km/h") or transmission gear ("Gang:").
                                // Motorized vehicles with a fuel tank (cars, motorcycles, trucks) have progress > 0.005f (shrinks as fuel is consumed).
                                // Unmotorized vehicles without a fuel tank (bicycles, skateboards, scooters) have progress <= 0.005f (0% / empty bar).
                                if (text.contains("km/h") || text.contains("Gang:") || text.contains("Höhe:") || text.contains("Hoehe:")) {
                                    boolean hasFuelTank = progress > 0.005f;
                                    addon.getBlitzerTracker().setTempomatAvailable(hasFuelTank);
                                    addon.getBlitzerTracker().setFuelPercent(hasFuelTank ? (progress * 100.0f) : -1.0f);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        this.vehicleBossBarAvailable = foundVehicleBar;
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
                I18n.get("gm-addon.setting.tacho.enabled"),
                I18n.get("gm-addon.setting.tacho.enabled.desc"),
                () -> cfg.tachoEnabled,
                val -> {
                    cfg.tachoEnabled = val;
                    addon.saveConfig();
                }
            ),
            new Setting.CycleSetting(
                "tacho_style",
                I18n.get("gm-addon.setting.tacho.style"),
                I18n.get("gm-addon.setting.tacho.style.desc"),
                List.of("ANALOG", "DIGITAL"),
                () -> cfg.tachoStyle != null ? cfg.tachoStyle.toUpperCase() : "ANALOG",
                val -> {
                    cfg.tachoStyle = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "tacho_only_vehicle",
                I18n.get("gm-addon.setting.tacho.only_in_vehicle"),
                I18n.get("gm-addon.setting.tacho.only_in_vehicle.desc"),
                () -> cfg.tachoOnlyInVehicle,
                val -> {
                    cfg.tachoOnlyInVehicle = val;
                    addon.saveConfig();
                }
            ),
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
            new Setting.IntSliderSetting(
                "tacho_max_kmh",
                I18n.get("gm-addon.setting.tacho.max_kmh"),
                I18n.get("gm-addon.setting.tacho.max_kmh.desc"),
                100, 260, " km/h",
                () -> cfg.tachoMaxKmh,
                val -> {
                    cfg.tachoMaxKmh = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "tacho_scale",
                I18n.get("gm-addon.setting.tacho.scale"),
                I18n.get("gm-addon.setting.tacho.scale.desc"),
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
