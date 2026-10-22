package de.pflanzenkaktus.enhancedgm.client.module;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.util.I18n;
import de.pflanzenkaktus.enhancedgm.config.AddonConfig;
import de.pflanzenkaktus.enhancedgm.feature.BlitzerWarningTracker;
import de.pflanzenkaktus.enhancedgm.module.HudWidgetDescriptor;
import de.pflanzenkaktus.enhancedgm.module.Setting;
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
                return I18n.get("gm-addon.widget.blitzer.name");
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
        return I18n.get("gm-addon.module.blitzer.name");
    }

    @Override
    public String getDescription() {
        return I18n.get("gm-addon.module.blitzer.desc");
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
            var result = addon.getCameraRepository().recordAutoDetectedCamera(
                px, py, pz, flashed.speedLimitKmh(), yaw, "community"
            );
            if (result == de.pflanzenkaktus.enhancedgm.data.SpeedCameraRepository.AddCameraResult.ADDED_NEW) {
                int rx = (int) Math.round(px);
                int rz = (int) Math.round(pz);
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    I18n.get("gm-addon.chat.blitzer_auto_saved", flashed.speedLimitKmh(), rx, rz)
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
                I18n.get("gm-addon.setting.blitzer.enable_hud"),
                I18n.get("gm-addon.setting.blitzer.enable_hud.desc"),
                () -> cfg.blitzerWidgetEnabled,
                val -> {
                    cfg.blitzerWidgetEnabled = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "blitzer_only_vehicle",
                I18n.get("gm-addon.setting.blitzer.only_vehicle"),
                I18n.get("gm-addon.setting.blitzer.only_vehicle.desc"),
                () -> cfg.blitzerOnlyInVehicle,
                val -> {
                    cfg.blitzerOnlyInVehicle = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "blitzer_auto_add",
                I18n.get("gm-addon.setting.blitzer.auto_learn"),
                I18n.get("gm-addon.setting.blitzer.auto_learn.desc"),
                () -> cfg.blitzerAutoAddOnFlash,
                val -> {
                    cfg.blitzerAutoAddOnFlash = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "blitzer_firebase_opt_in",
                I18n.get("gm-addon.setting.blitzer.cloud_sync"),
                I18n.get("gm-addon.setting.blitzer.cloud_sync.desc"),
                () -> cfg.enableFirebaseCloudSync,
                val -> {
                    cfg.enableFirebaseCloudSync = val;
                    cfg.firebaseConsentPromptDismissed = true;
                    addon.saveConfig();
                    if (val) {
                        addon.getCameraRepository().syncWithFirebaseAsync();
                    } else {
                        addon.getCameraRepository().setSyncStatus(I18n.get("gm-addon.setting.blitzer.local_status", addon.getCameraRepository().getCameras().size()));
                    }
                }
            ),
            new Setting.BooleanSetting(
                "blitzer_upload_firebase",
                I18n.get("gm-addon.setting.blitzer.upload_firebase"),
                I18n.get("gm-addon.setting.blitzer.upload_firebase.desc"),
                () -> cfg.blitzerUploadAutoToFirebase,
                val -> {
                    cfg.blitzerUploadAutoToFirebase = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "blitzer_dedupe_radius",
                I18n.get("gm-addon.setting.blitzer.dedupe_radius"),
                I18n.get("gm-addon.setting.blitzer.dedupe_radius.desc"),
                10.0, 80.0, 2.0, " m",
                () -> cfg.blitzerDedupeRadiusBlocks,
                val -> {
                    cfg.blitzerDedupeRadiusBlocks = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "blitzer_3d_markers",
                I18n.get("gm-addon.setting.blitzer.enable_3d_markers"),
                I18n.get("gm-addon.setting.blitzer.enable_3d_markers.desc"),
                () -> cfg.render3dWorldMarkers,
                val -> {
                    cfg.render3dWorldMarkers = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "blitzer_sound",
                I18n.get("gm-addon.setting.blitzer.sound_warning"),
                I18n.get("gm-addon.setting.blitzer.sound_warning.desc"),
                () -> cfg.playWarningSound,
                val -> {
                    cfg.playWarningSound = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "blitzer_warn_dist",
                I18n.get("gm-addon.setting.blitzer.warning_distance"),
                I18n.get("gm-addon.setting.blitzer.warning_distance.desc"),
                30.0, 250.0, 5.0, " m",
                () -> cfg.warningDistanceBlocks,
                val -> {
                    cfg.warningDistanceBlocks = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "blitzer_urgent_dist",
                I18n.get("gm-addon.setting.blitzer.urgent_distance"),
                I18n.get("gm-addon.setting.blitzer.urgent_distance.desc"),
                15.0, 120.0, 5.0, " m",
                () -> cfg.urgentDistanceBlocks,
                val -> {
                    cfg.urgentDistanceBlocks = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "blitzer_cone_deg",
                I18n.get("gm-addon.setting.blitzer.cone_deg"),
                I18n.get("gm-addon.setting.blitzer.cone_deg.desc"),
                25.0, 180.0, 5.0, "°",
                () -> cfg.directionConeDegrees,
                val -> {
                    cfg.directionConeDegrees = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "blitzer_scale",
                I18n.get("gm-addon.setting.blitzer.scale"),
                I18n.get("gm-addon.setting.blitzer.scale.desc"),
                0.6, 2.0, 0.05, "x",
                () -> (double) cfg.blitzerWidgetScale,
                val -> {
                    cfg.blitzerWidgetScale = val.floatValue();
                    addon.saveConfig();
                }
            ),
            new Setting.ActionSetting(
                "reload_blitzer_db",
                I18n.get("gm-addon.setting.blitzer.sync_now"),
                I18n.get("gm-addon.setting.blitzer.sync_now.btn"),
                I18n.get("gm-addon.setting.blitzer.sync_now.desc"),
                () -> {
                    addon.getCameraRepository().loadFromLocalDatabase();
                    if (cfg.enableFirebaseCloudSync) {
                        addon.getCameraRepository().syncWithFirebaseAsync();
                    }
                }
            )
        );
    }
}
