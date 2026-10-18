package de.germanminer.addon.client.module;

import de.germanminer.addon.client.GMAddonClient;
import de.germanminer.addon.config.AddonConfig;
import de.germanminer.addon.feature.BlitzerWarningTracker;
import de.germanminer.addon.module.HudWidgetDescriptor;
import de.germanminer.addon.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.Optional;

/**
 * Modular Speed Camera ("Blitzer") Warning feature module.
 */
public class BlitzerModule implements ClientFeatureModule {
    private final GMAddonClient addon;
    private final HudWidgetDescriptor widgetDescriptor;

    public BlitzerModule(GMAddonClient addon) {
        this.addon = addon;
        this.widgetDescriptor = new HudWidgetDescriptor() {
            @Override
            public String getWidgetId() {
                return "blitzer_alert";
            }

            @Override
            public String getDisplayName() {
                return "Blitzer-Warnung";
            }

            @Override
            public boolean isEnabled() {
                return addon.getConfig().blitzerWidgetEnabled;
            }

            @Override
            public void setEnabled(boolean enabled) {
                addon.getConfig().blitzerWidgetEnabled = enabled;
                addon.saveConfig();
            }

            @Override
            public int getX() {
                return addon.getConfig().blitzerWidgetX;
            }

            @Override
            public int getY() {
                return addon.getConfig().blitzerWidgetY;
            }

            @Override
            public void setPosition(int x, int y) {
                addon.getConfig().blitzerWidgetX = x;
                addon.getConfig().blitzerWidgetY = y;
            }

            @Override
            public float getScale() {
                return addon.getConfig().blitzerWidgetScale;
            }

            @Override
            public void setScale(float scale) {
                addon.getConfig().blitzerWidgetScale = scale;
            }

            @Override
            public int getBaseWidth() {
                return 186;
            }

            @Override
            public int getBaseHeight() {
                return 46;
            }

            @Override
            public void resetToDefault() {
                AddonConfig cfg = addon.getConfig();
                cfg.blitzerWidgetX = 160;
                cfg.blitzerWidgetY = 14;
                cfg.blitzerWidgetScale = 1.0f;
            }
        };
    }

    @Override
    public String getId() {
        return "blitzer";
    }

    @Override
    public String getDisplayName() {
        return "Blitzer-Warnsystem";
    }

    @Override
    public String getDescription() {
        return "Warnt vor Geschwindigkeitskameras (HUD, Warnton, 3D-Marker & Firebase-Sync).";
    }

    @Override
    public boolean isEnabled() {
        return addon.getConfig().blitzerWidgetEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        addon.getConfig().blitzerWidgetEnabled = enabled;
        addon.saveConfig();
    }

    @Override
    public Optional<HudWidgetDescriptor> getHudWidget() {
        return Optional.of(widgetDescriptor);
    }

    @Override
    public void onServerJoin(GMAddonClient addon) {
        addon.getCameraRepository().syncWithFirebaseAsync();
    }

    @Override
    public void onChatMessage(GMAddonClient addon, String plainMessage) {
        AddonConfig cfg = addon.getConfig();
        if (!cfg.blitzerAutoAddOnFlash) {
            return;
        }
        BlitzerWarningTracker.parseFlashedChatMessage(plainMessage).ifPresent(flashed -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            double px = mc.player.getX();
            double py = mc.player.getY();
            double pz = mc.player.getZ();
            float yaw = mc.player.getYRot();
            String playerName = mc.player.getName().getString();

            var result = addon.getCameraRepository().recordAutoDetectedCamera(
                px, py, pz, flashed.speedLimitKmh(), yaw, playerName
            );
            if (result == de.germanminer.addon.data.SpeedCameraRepository.AddCameraResult.ADDED_NEW) {
                int rx = (int) Math.round(px);
                int rz = (int) Math.round(pz);
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§b§l[GM-Addon] §aNeuer Blitzer automatisch gespeichert! §7(§e"
                        + flashed.speedLimitKmh() + " km/h Zone §7bei §f" + rx + ", " + rz + "§7) §8[Auto-Added]"
                ));
            }
        });
    }

    @Override
    public void onActionBarMessage(GMAddonClient addon, String plainMessage) {
        addon.getBlitzerTracker().onActionBarMessage(plainMessage);
    }

    @Override
    public void onClientTick(GMAddonClient addon, Minecraft client) {
        if (client.player == null) return;
        if (addon.getBlitzerTracker().consumePendingWarningSound()) {
            client.player.playSound(
                SoundEvents.NOTE_BLOCK_PLING.value(),
                1.0f,
                addon.getBlitzerTracker().getPendingWarningSoundPitch()
            );
        }
    }

    @Override
    public void renderWorldOverlay(GMAddonClient addon, GuiGraphicsExtractor graphics, Minecraft mc, float partialTick) {
        AddonConfig cfg = addon.getConfig();
        if (cfg.render3dWorldMarkers) {
            if (cfg.blitzerOnlyInVehicle && (mc.player == null || (!mc.player.isPassenger() && mc.player.getVehicle() == null))) {
                return;
            }
            addon.getHudRenderer().render3dWorldBlitzerMarkers(graphics, mc, partialTick);
        }
    }

    @Override
    public void renderHudWidget(GMAddonClient addon, GuiGraphicsExtractor graphics, Minecraft mc, float partialTick, boolean editorPreview) {
        addon.getHudRenderer().renderBlitzerAlertWidget(graphics, mc, editorPreview);
    }

    @Override
    public List<Setting<?>> getSettings() {
        AddonConfig cfg = addon.getConfig();
        return List.of(
            new Setting.BooleanSetting(
                "blitzer_enabled",
                "Blitzer-Warnung aktivieren",
                "Zeigt das Blitzer-Warnwidget bei Annäherung an eine Kamera.",
                () -> cfg.blitzerWidgetEnabled,
                val -> {
                    cfg.blitzerWidgetEnabled = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "blitzer_only_vehicle",
                "Nur im Fahrzeug aktiv",
                "Aktiviert Blitzer-Warnungen, Warntöne und 3D-Marker nur, wenn du in einem Fahrzeug sitzt.",
                () -> cfg.blitzerOnlyInVehicle,
                val -> {
                    cfg.blitzerOnlyInVehicle = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "blitzer_auto_add",
                "Geblitzte Kameras automatisch speichern",
                "Erkennt im Chat wenn du geblitzt wirst und speichert den Standort automatisch (mit autoAdded-Flag).",
                () -> cfg.blitzerAutoAddOnFlash,
                val -> {
                    cfg.blitzerAutoAddOnFlash = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "blitzer_upload_firebase",
                "Auto-Blitzer zu Firebase hochladen",
                "Lädt neu erkannte Blitzer automatisch in deine Firebase-Datenbank hoch (sobald URL konfiguriert).",
                () -> cfg.blitzerUploadAutoToFirebase,
                val -> {
                    cfg.blitzerUploadAutoToFirebase = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "blitzer_dedupe_radius",
                "Mindestabstand (Kein Stapeln)",
                "Verhindert doppelte Blitzer am selben Ort (alle Kameras im Radius werden zusammengeführt).",
                10.0, 80.0, 2.0, " m",
                () -> cfg.blitzerDedupeRadiusBlocks,
                val -> {
                    cfg.blitzerDedupeRadiusBlocks = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "blitzer_3d_markers",
                "3D Welt-Marker anzeigen",
                "Zeigt schwebende Blitzer-Markierungen mit Distanz direkt in der 3D-Welt.",
                () -> cfg.render3dWorldMarkers,
                val -> {
                    cfg.render3dWorldMarkers = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "blitzer_sound",
                "Akustischer Warnton",
                "Spielt einen Warnton ab, wenn du auf einen Blitzer zufährst oder zu schnell bist.",
                () -> cfg.playWarningSound,
                val -> {
                    cfg.playWarningSound = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "blitzer_warn_dist",
                "Warn-Distanz (Vorwarnung)",
                "Ab welcher Entfernung (in Blöcken/Metern) vor einem Blitzer gewarnt wird.",
                30.0, 250.0, 5.0, " m",
                () -> cfg.warningDistanceBlocks,
                val -> {
                    cfg.warningDistanceBlocks = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "blitzer_urgent_dist",
                "Akute Warn-Distanz (Blinken)",
                "Entfernung für die dringende rote/orange Warnstufe.",
                15.0, 120.0, 5.0, " m",
                () -> cfg.urgentDistanceBlocks,
                val -> {
                    cfg.urgentDistanceBlocks = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "blitzer_cone_deg",
                "Richtungskegel (Winkel)",
                "Winkelkegel in Fahrtrichtung/Blickrichtung zur Erkennung (180° = rundum).",
                25.0, 180.0, 5.0, "°",
                () -> cfg.directionConeDegrees,
                val -> {
                    cfg.directionConeDegrees = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "blitzer_scale",
                "Blitzer-Widget Skalierung",
                "Größenskalierung des Blitzer-Widgets im HUD.",
                0.6, 2.0, 0.05, "x",
                () -> (double) cfg.blitzerWidgetScale,
                val -> {
                    cfg.blitzerWidgetScale = val.floatValue();
                    addon.saveConfig();
                }
            ),
            new Setting.ActionSetting(
                "reload_blitzer_db",
                "Blitzer-Datenbank neu synchronisieren",
                "Jetzt synchronisieren",
                "Lädt die Blitzer-Datenbank neu und synchronisiert mit der Community-Cloud.",
                () -> {
                    addon.getCameraRepository().loadFromLocalDatabase();
                    addon.getCameraRepository().syncWithFirebaseAsync();
                }
            )
        );
    }
}
