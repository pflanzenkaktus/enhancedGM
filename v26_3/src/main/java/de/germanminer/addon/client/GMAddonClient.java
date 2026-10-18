package de.germanminer.addon.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.germanminer.addon.client.compat.VersionCompat;
import de.germanminer.addon.client.map.BlueMapTextureCache;
import de.germanminer.addon.client.module.ModuleManager;
import de.germanminer.addon.client.render.HudOverlayRenderer;
import de.germanminer.addon.client.screen.DamageLogScreen;
import de.germanminer.addon.client.screen.FullscreenMapScreen;
import de.germanminer.addon.client.screen.GMSettingsScreen;
import de.germanminer.addon.client.screen.HudEditorScreen;
import de.germanminer.addon.config.AddonConfig;
import de.germanminer.addon.data.SpeedCameraRepository;
import de.germanminer.addon.feature.BlitzerWarningTracker;
import de.germanminer.addon.feature.DamageLogTracker;
import de.germanminer.addon.feature.SpawnProtectionTracker;
import de.germanminer.addon.map.BlueMapMarkerClient;
import de.germanminer.addon.map.BlueMapTileClient;
import de.germanminer.addon.map.LocalWebMapBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Main client entrypoint for GermanMiner Addon (Minecraft 26.3).
 */
public class GMAddonClient implements ClientModInitializer {
    private static GMAddonClient instance;

    private Path configDir;
    private Path configFile;
    private AddonConfig config;

    private SpeedCameraRepository cameraRepository;
    private BlueMapTileClient tileClient;
    private BlueMapMarkerClient markerClient;
    private BlueMapTextureCache textureCache;
    private BlitzerWarningTracker blitzerTracker;
    private SpawnProtectionTracker spawnProtectionTracker;
    private DamageLogTracker damageLogTracker;
    private LocalWebMapBridge webMapBridge;
    private HudOverlayRenderer hudRenderer;
    private ModuleManager moduleManager;

    private KeyMapping openMapKey;
    private KeyMapping openHudEditorKey;
    private KeyMapping openSettingsKey;
    private KeyMapping openDamageLogKey;
    private KeyMapping zoomInKey;
    private KeyMapping zoomOutKey;
    private KeyMapping tempomatToggleKey;
    private KeyMapping tempomatPlusKey;
    private KeyMapping tempomatMinusKey;

    private int tickCounter = 0;
    private boolean wasDeadOrDying = false;
    private int lastPlayerEntityId = -1;
    private String lastChatHoverText = "";

    // Anti-spam queue & edge tracking for Tempomat hotkeys
    private boolean wasTempomatToggleDown = false;
    private boolean wasTempomatPlusDown = false;
    private boolean wasTempomatMinusDown = false;
    private int holdTicksPlus = 0;
    private int holdTicksMinus = 0;
    private boolean pendingTempomatToggle = false;
    private int pendingTempomatDeltaSteps = 0;
    private long lastTempomatCommandTimeMs = 0L;
    private static final long TEMPOMAT_COMMAND_COOLDOWN_MS = 500L;

    @Override
    public void onInitializeClient() {
        instance = this;

        this.configDir = FabricLoader.getInstance().getConfigDir().resolve("gm-addon");
        this.configFile = configDir.resolve("config.json");
        this.config = AddonConfig.load(configFile);

        this.cameraRepository = new SpeedCameraRepository(configDir.resolve("speed_cameras.json"), config);
        this.cameraRepository.initialize();

        this.tileClient = new BlueMapTileClient(configDir.resolve("tile_cache"), config);
        this.markerClient = new BlueMapMarkerClient(config);
        this.markerClient.refreshAsync();

        this.textureCache = new BlueMapTextureCache(tileClient);
        this.blitzerTracker = new BlitzerWarningTracker(config);
        this.spawnProtectionTracker = new SpawnProtectionTracker(config);
        this.damageLogTracker = new DamageLogTracker(config);
        this.webMapBridge = new LocalWebMapBridge(config, cameraRepository);
        this.webMapBridge.startIfEnabled();

        this.hudRenderer = new HudOverlayRenderer(this);
        this.moduleManager = new ModuleManager(this);
        this.moduleManager.registerDefaultModules();

        registerKeyMappings();
        registerEvents();
    }

    private void registerKeyMappings() {
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("gm-addon", "general")
        );

        openMapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.gm-addon.open_map",
            InputConstants.KEY_M,
            category
        ));
        openHudEditorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.gm-addon.open_hud_editor",
            InputConstants.KEY_H,
            category
        ));
        openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.gm-addon.open_settings",
            InputConstants.KEY_O,
            category
        ));
        openDamageLogKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.gm-addon.open_damagelog",
            InputConstants.KEY_L,
            category
        ));
        zoomInKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.gm-addon.zoom_in",
            InputConstants.KEY_EQUALS,
            category
        ));
        zoomOutKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.gm-addon.zoom_out",
            InputConstants.KEY_MINUS,
            category
        ));
        tempomatToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.gm-addon.tempomat_toggle",
            InputConstants.KEY_G,
            category
        ));
        tempomatPlusKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.gm-addon.tempomat_plus",
            InputConstants.KEY_UP,
            category
        ));
        tempomatMinusKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.gm-addon.tempomat_minus",
            InputConstants.KEY_DOWN,
            category
        ));
    }

    private void registerEvents() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("gm-addon", "hud_overlay"),
            (graphics, deltaTracker) -> hudRenderer.renderHud(graphics, deltaTracker)
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            lastPlayerEntityId = -1;
            wasDeadOrDying = false;
            if (isActiveOnCurrentServer()) {
                moduleManager.onServerJoin();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            moduleManager.onServerDisconnect();
            lastPlayerEntityId = -1;
            wasDeadOrDying = false;
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!isActiveOnCurrentServer() || message == null) {
                return;
            }
            String plain = message.getString();
            if (overlay) {
                moduleManager.onActionBarMessage(plain);
            } else {
                StringBuilder hoverBuilder = new StringBuilder();
                extractHoverTexts(message, hoverBuilder);
                this.lastChatHoverText = hoverBuilder.toString().trim();
                moduleManager.onChatMessage(plain);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void extractHoverTexts(Component component, StringBuilder out) {
        if (component == null) return;
        HoverEvent hover = component.getStyle().getHoverEvent();
        if (hover instanceof HoverEvent.ShowText showText && showText.value() != null) {
            String t = showText.value().getString().trim();
            if (!t.isEmpty()) {
                if (!out.isEmpty()) out.append(" | ");
                out.append(t);
            }
        }
        for (Component sibling : component.getSiblings()) {
            extractHoverTexts(sibling, out);
        }
    }

    private void onClientTick(Minecraft client) {
        while (openMapKey.consumeClick()) {
            VersionCompat.setScreen(client, new FullscreenMapScreen(this, VersionCompat.getScreen(client)));
        }
        while (openHudEditorKey.consumeClick()) {
            VersionCompat.setScreen(client, new HudEditorScreen(this, VersionCompat.getScreen(client)));
        }
        while (openSettingsKey.consumeClick()) {
            VersionCompat.setScreen(client, new GMSettingsScreen(this, VersionCompat.getScreen(client)));
        }
        while (openDamageLogKey.consumeClick()) {
            VersionCompat.setScreen(client, new DamageLogScreen(this, VersionCompat.getScreen(client)));
        }
        while (zoomInKey.consumeClick()) {
            config.minimapZoomBlocks = Math.max(50.0, config.minimapZoomBlocks * 0.8);
            saveConfig();
        }
        while (zoomOutKey.consumeClick()) {
            config.minimapZoomBlocks = Math.min(800.0, config.minimapZoomBlocks * 1.25);
            saveConfig();
        }
        // Drain raw clicks so Minecraft doesn't buffer OS key-repeats
        while (tempomatToggleKey.consumeClick()) {}
        while (tempomatPlusKey.consumeClick()) {}
        while (tempomatMinusKey.consumeClick()) {}

        if (client.player == null || !isActiveOnCurrentServer()) {
            wasTempomatToggleDown = false;
            wasTempomatPlusDown = false;
            wasTempomatMinusDown = false;
            pendingTempomatToggle = false;
            pendingTempomatDeltaSteps = 0;
            return;
        }

        boolean inVehicle = client.player.isPassenger() || client.player.getVehicle() != null;
        if (!inVehicle) {
            pendingTempomatToggle = false;
            pendingTempomatDeltaSteps = 0;
        } else if (VersionCompat.getScreen(client) == null) {
            // Cruise control auto-disable when braking (pressing 'S')
            boolean isBraking = client.options.keyDown.isDown();
            if (isBraking && blitzerTracker.isTempomatActive()) {
                blitzerTracker.disengageTempomatOnBrake();
                pendingTempomatToggle = false;
                pendingTempomatDeltaSteps = 0;
            }

            boolean toggleDown = tempomatToggleKey.isDown();
            if (toggleDown && !wasTempomatToggleDown) {
                if (!blitzerTracker.isTempomatAvailable()) {
                    blitzerTracker.setTempomatAvailable(true);
                }
                blitzerTracker.onTempomatToggleCommand();
                pendingTempomatToggle = !pendingTempomatToggle;
            }
            wasTempomatToggleDown = toggleDown;

            boolean plusDown = tempomatPlusKey.isDown();
            if (plusDown) {
                holdTicksPlus++;
                if (!wasTempomatPlusDown || (holdTicksPlus >= 12 && holdTicksPlus % 10 == 0)) {
                    blitzerTracker.onTempomatPlusCommand();
                    pendingTempomatDeltaSteps = Math.clamp(pendingTempomatDeltaSteps + 1, -15, 15);
                }
            } else {
                holdTicksPlus = 0;
            }
            wasTempomatPlusDown = plusDown;

            boolean minusDown = tempomatMinusKey.isDown();
            if (minusDown) {
                holdTicksMinus++;
                if (!wasTempomatMinusDown || (holdTicksMinus >= 12 && holdTicksMinus % 10 == 0)) {
                    blitzerTracker.onTempomatMinusCommand();
                    pendingTempomatDeltaSteps = Math.clamp(pendingTempomatDeltaSteps - 1, -15, 15);
                }
            } else {
                holdTicksMinus = 0;
            }
            wasTempomatMinusDown = minusDown;

            // Dispatch queued Tempomat commands at a safe anti-spam rate (1 command per 500ms)
            long nowMs = System.currentTimeMillis();
            if (client.player.connection != null && (nowMs - lastTempomatCommandTimeMs) >= TEMPOMAT_COMMAND_COOLDOWN_MS) {
                if (pendingTempomatToggle) {
                    pendingTempomatToggle = false;
                    lastTempomatCommandTimeMs = nowMs;
                    client.player.connection.sendCommand("vehicles tempomat");
                } else if (pendingTempomatDeltaSteps > 0) {
                    pendingTempomatDeltaSteps--;
                    lastTempomatCommandTimeMs = nowMs;
                    client.player.connection.sendCommand("vehicles tempomat-plus");
                } else if (pendingTempomatDeltaSteps < 0) {
                    pendingTempomatDeltaSteps++;
                    lastTempomatCommandTimeMs = nowMs;
                    client.player.connection.sendCommand("vehicles tempomat-minus");
                }
            }
        }

        // Update player/vehicle movement speed telemetry and proximity alerts once per tick
        var rootEntity = client.player.getRootVehicle() != null ? client.player.getRootVehicle() : client.player;
        blitzerTracker.updatePlayerState(
            rootEntity.getX(),
            rootEntity.getY(),
            rootEntity.getZ(),
            client.player.getYRot(),
            inVehicle,
            cameraRepository.getCameras()
        );

        // Detect player respawn
        int currentEntityId = client.player.getId();
        boolean currentlyDead = client.player.isDeadOrDying() || client.player.getHealth() <= 0.0f;
        if (wasDeadOrDying && !currentlyDead) {
            moduleManager.onPlayerRespawn();
        } else if (lastPlayerEntityId != -1 && currentEntityId != lastPlayerEntityId && !currentlyDead) {
            moduleManager.onPlayerRespawn();
        }
        wasDeadOrDying = currentlyDead;
        lastPlayerEntityId = currentEntityId;

        // Tick all enabled modules
        moduleManager.onClientTick(client);

        // Update localhost second-monitor web map bridge
        webMapBridge.updatePlayerTelemetry(
            client.player.getX(),
            client.player.getY(),
            client.player.getZ(),
            client.player.getYRot(),
            blitzerTracker.getCurrentSpeedKmh(),
            blitzerTracker.getCurrentAlert()
        );

        tickCounter++;
        if (tickCounter % 40 == 0) {
            cameraRepository.tickCheckForUpdates();
            markerClient.tickCheckForRefresh();
        }
    }

    public boolean isActiveOnCurrentServer() {
        if (config.forceEnableEverywhere) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        ServerData serverData = mc.getCurrentServer();
        if (serverData == null || serverData.ip == null) {
            return false;
        }
        String ip = serverData.ip.toLowerCase(Locale.ROOT);
        String pattern = config.serverDomainPattern != null
            ? config.serverDomainPattern.toLowerCase(Locale.ROOT)
            : "germanminer.de";
        return ip.contains(pattern);
    }

    public void saveConfig() {
        config.save(configFile);
    }

    public static GMAddonClient getInstance() {
        return instance;
    }

    public AddonConfig getConfig() {
        return config;
    }

    public SpeedCameraRepository getCameraRepository() {
        return cameraRepository;
    }

    public BlueMapTileClient getTileClient() {
        return tileClient;
    }

    public BlueMapMarkerClient getMarkerClient() {
        return markerClient;
    }

    public BlueMapTextureCache getTextureCache() {
        return textureCache;
    }

    public BlitzerWarningTracker getBlitzerTracker() {
        return blitzerTracker;
    }

    public SpawnProtectionTracker getSpawnProtectionTracker() {
        return spawnProtectionTracker;
    }

    public DamageLogTracker getDamageLogTracker() {
        return damageLogTracker;
    }

    public String getLastChatHoverText() {
        return lastChatHoverText;
    }

    public LocalWebMapBridge getWebMapBridge() {
        return webMapBridge;
    }

    public HudOverlayRenderer getHudRenderer() {
        return hudRenderer;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public boolean hasPendingTempomatCommands() {
        return pendingTempomatToggle || pendingTempomatDeltaSteps != 0;
    }
}
