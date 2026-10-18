package de.germanminer.addon.client.module;

import de.germanminer.addon.client.GMAddonClient;
import de.germanminer.addon.client.compat.VersionCompat;
import de.germanminer.addon.client.screen.DamageLogScreen;
import de.germanminer.addon.config.AddonConfig;
import de.germanminer.addon.feature.DamageLogTracker;
import de.germanminer.addon.module.HudWidgetDescriptor;
import de.germanminer.addon.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Modular Damage-Log & 30-second Notwehr (self-defense) feature module.
 */
public class DamageLogModule implements ClientFeatureModule {
    private final GMAddonClient addon;
    private final HudWidgetDescriptor widgetDescriptor;
    private int lastHurtTime = 0;

    public DamageLogModule(GMAddonClient addon) {
        this.addon = addon;
        this.widgetDescriptor = new HudWidgetDescriptor() {
            @Override
            public String getWidgetId() {
                return "notwehr_timer";
            }

            @Override
            public String getDisplayName() {
                return "Notwehr-Timer (30s)";
            }

            @Override
            public boolean isEnabled() {
                return addon.getConfig().notwehrWidgetEnabled;
            }

            @Override
            public void setEnabled(boolean enabled) {
                addon.getConfig().notwehrWidgetEnabled = enabled;
                addon.saveConfig();
            }

            @Override
            public int getX() {
                return addon.getConfig().notwehrWidgetX;
            }

            @Override
            public int getY() {
                return addon.getConfig().notwehrWidgetY;
            }

            @Override
            public void setPosition(int x, int y) {
                addon.getConfig().notwehrWidgetX = x;
                addon.getConfig().notwehrWidgetY = y;
            }

            @Override
            public float getScale() {
                return addon.getConfig().notwehrWidgetScale;
            }

            @Override
            public void setScale(float scale) {
                addon.getConfig().notwehrWidgetScale = scale;
            }

            @Override
            public int getBaseWidth() {
                return 175;
            }

            @Override
            public int getBaseHeight() {
                return 38;
            }

            @Override
            public void resetToDefault() {
                AddonConfig cfg = addon.getConfig();
                cfg.notwehrWidgetX = 12;
                cfg.notwehrWidgetY = 216;
                cfg.notwehrWidgetScale = 1.0f;
            }
        };
    }

    @Override
    public String getId() {
        return "damagelog";
    }

    @Override
    public String getDisplayName() {
        return "Damage-Log & Notwehr";
    }

    @Override
    public String getDescription() {
        return "Prüft /damagelog auf die 30-Sekunden-Notwehr-Regel und zeigt einen Live-Notwehr-Timer im HUD.";
    }

    @Override
    public boolean isEnabled() {
        return addon.getConfig().damageLogEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        addon.getConfig().damageLogEnabled = enabled;
        addon.saveConfig();
    }

    @Override
    public Optional<HudWidgetDescriptor> getHudWidget() {
        return Optional.of(widgetDescriptor);
    }

    @Override
    public void onChatMessage(GMAddonClient addon, String plainMessage) {
        Minecraft mc = Minecraft.getInstance();
        String localName = (mc.player != null) ? mc.player.getName().getString() : "";
        addon.getDamageLogTracker().onChatMessage(plainMessage, addon.getLastChatHoverText(), addon.getLastChatHoverText(), localName);
    }

    @Override
    public void onClientTick(GMAddonClient addon, Minecraft client) {
        if (client.player == null || client.level == null) {
            lastHurtTime = 0;
            return;
        }

        AddonConfig cfg = addon.getConfig();
        DamageLogTracker tracker = addon.getDamageLogTracker();

        // 1. Live incoming player hit detection for real-time 30s Notwehr HUD countdown
        int currentHurt = client.player.hurtTime;
        if (cfg.notwehrLiveHitDetection && currentHurt > 0 && currentHurt > lastHurtTime) {
            Optional<AbstractClientPlayer> nearestAttacker = client.level.players().stream()
                .filter(p -> p != client.player && !p.isSpectator())
                .filter(p -> p.distanceToSqr(client.player) <= 49.0) // within 7 blocks
                .min(Comparator.comparingDouble(p -> p.distanceToSqr(client.player)));

            if (nearestAttacker.isPresent()) {
                String attackerName = nearestAttacker.get().getName().getString();
                tracker.triggerLiveIncomingHit(attackerName);
            }
        }
        lastHurtTime = currentHurt;

        // 2. Print automatic Notwehr summary in chat once a /damagelog finishes arriving
        if (cfg.damageLogAutoChatSummary && tracker.consumePendingLogSummaryIfReady()) {
            List<DamageLogTracker.OpponentSummary> summaries = tracker.getOpponentSummaries();
            if (!summaries.isEmpty()) {
                client.player.sendSystemMessage(Component.literal(
                    "§8§m-----------------------------------------------------"
                ));
                client.player.sendSystemMessage(Component.literal(
                    "§b§l[GM-Addon] §f§lNotwehr-Analyse §7(" + cfg.notwehrWindowSeconds + "s Regel) §8— §eTaste [L] für Details"
                ));
                for (DamageLogTracker.OpponentSummary sum : summaries) {
                    String prefix = switch (sum.overallStatus()) {
                        case VALID_NOTWEHR -> "§a✔ ";
                        case INCOMING_ATTACK -> "§bℹ ";
                        case EXPIRED_NOTWEHR -> "§6⚠ ";
                        case FIRST_STRIKE_BY_SELF -> "§c✖ ";
                    };
                    client.player.sendSystemMessage(Component.literal(
                        " " + prefix + "§f" + sum.summaryVerdict()
                    ));
                }
                client.player.sendSystemMessage(Component.literal(
                    "§8§m-----------------------------------------------------"
                ));
            }
        }
    }

    @Override
    public void renderHudWidget(GMAddonClient addon, GuiGraphicsExtractor graphics, Minecraft mc, float partialTick, boolean editorPreview) {
        addon.getHudRenderer().renderNotwehrWidget(graphics, mc, editorPreview);
    }

    @Override
    public List<Setting<?>> getSettings() {
        AddonConfig cfg = addon.getConfig();
        return List.of(
            new Setting.BooleanSetting(
                "damagelog_enabled",
                "Damage-Log & Notwehr Modul aktivieren",
                "Aktiviert die automatische /damagelog Auswertung und den 30s Notwehr-Tracker.",
                () -> cfg.damageLogEnabled,
                val -> {
                    cfg.damageLogEnabled = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "notwehr_widget_enabled",
                "Notwehr HUD-Widget anzeigen",
                "Zeigt einen 30s Countdown im HUD, sobald dich ein Spieler angreift.",
                () -> cfg.notwehrWidgetEnabled,
                val -> {
                    cfg.notwehrWidgetEnabled = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "notwehr_live_detection",
                "Live-Treffererkennung im Kampf",
                "Startet den 30s Notwehr-Timer sofort live, wenn ein Spieler in deiner Nähe dich schlägt.",
                () -> cfg.notwehrLiveHitDetection,
                val -> {
                    cfg.notwehrLiveHitDetection = val;
                    addon.saveConfig();
                }
            ),
            new Setting.BooleanSetting(
                "damagelog_chat_summary",
                "Automatische Chat-Zusammenfassung",
                "Gibt direkt unter /damagelog im Chat aus, ob deine Gegenwehr innerhalb der 30s Notwehr lag.",
                () -> cfg.damageLogAutoChatSummary,
                val -> {
                    cfg.damageLogAutoChatSummary = val;
                    addon.saveConfig();
                }
            ),
            new Setting.IntSliderSetting(
                "notwehr_window_sec",
                "Notwehr-Zeitfenster (Sekunden)",
                "Erlaubte Zeitspanne für straffreie Gegenwehr nach einem erlittenen Treffer (Standard: 30s).",
                10, 60, " s",
                () -> cfg.notwehrWindowSeconds,
                val -> {
                    cfg.notwehrWindowSeconds = val;
                    addon.saveConfig();
                }
            ),
            new Setting.DoubleSliderSetting(
                "notwehr_scale",
                "Notwehr-Widget Skalierung",
                "Größenskalierung des Notwehr-Countdown-Widgets im HUD.",
                0.6, 2.0, 0.05, "x",
                () -> (double) cfg.notwehrWidgetScale,
                val -> {
                    cfg.notwehrWidgetScale = val.floatValue();
                    addon.saveConfig();
                }
            ),
            new Setting.ActionSetting(
                "open_damagelog_screen",
                "Damage-Log Prüfer öffnen (Taste L)",
                "Prüfer öffnen",
                "Öffnet die detaillierte Übersicht aller erlittenen und zugefügten Treffer inkl. 30s-Analyse.",
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    VersionCompat.setScreen(mc, new DamageLogScreen(addon, VersionCompat.getScreen(mc)));
                }
            ),
            new Setting.ActionSetting(
                "test_notwehr_timer",
                "Notwehr-Countdown testen",
                "30s Test starten",
                "Startet den Notwehr-Countdown testweise für 30 Sekunden im HUD.",
                () -> addon.getDamageLogTracker().triggerLiveIncomingHit("TestAngreifer")
            )
        );
    }
}
