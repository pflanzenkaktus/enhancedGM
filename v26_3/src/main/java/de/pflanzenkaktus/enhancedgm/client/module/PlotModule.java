package de.pflanzenkaktus.enhancedgm.client.module;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import de.pflanzenkaktus.enhancedgm.client.compat.VersionCompat;
import de.pflanzenkaktus.enhancedgm.client.screen.PlotOverviewScreen;
import de.pflanzenkaktus.enhancedgm.client.screen.PlotScanPromptScreen;
import de.pflanzenkaktus.enhancedgm.client.util.I18n;
import de.pflanzenkaktus.enhancedgm.config.AddonConfig;
import de.pflanzenkaktus.enhancedgm.data.PlotRepository;
import de.pflanzenkaktus.enhancedgm.feature.PlotTracker;
import de.pflanzenkaktus.enhancedgm.model.OwnedPlot;
import de.pflanzenkaktus.enhancedgm.module.HudWidgetDescriptor;
import de.pflanzenkaktus.enhancedgm.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Plot expiry reminder: GermanMiner clears plots / apartments that are not entered for 60 days.
 * Scans /menu -> "Grundstücke &amp; Wohnungen", tracks verified visits via the owner action bar message
 * and reminds the player before a plot expires.
 */
public class PlotModule implements ClientFeatureModule {
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private enum ScanState { IDLE, WAIT_MENU, WAIT_REGIONS }

    private static final int SCAN_TIMEOUT_TICKS = 200;
    private static final int SETTLE_TICKS = 8;
    private static final int JOIN_REMINDER_DELAY_TICKS = 100;
    private static final long HINT_COOLDOWN_MS = 60_000L;
    private static final long FEEDBACK_MIN_GAP_MS = 60L * 60L * 1000L;

    private final GMAddonClient addon;
    private final HudWidgetDescriptor widgetDescriptor;

    private static final int MAX_SCAN_PAGES = 20;
    private final List<OwnedPlot> scanAccum = new ArrayList<>();
    private int scanPages = 0;

    private ScanState scanState = ScanState.IDLE;
    private int scanTicks = 0;
    private int settleTicks = 0;
    private Object lastScreenRef = null;
    private int joinReminderTicks = -1;
    private int periodicReminderTicks = 0;
    private long lastHintMs = 0L;
    private String lastHintPlotKey = "";

    public PlotModule(GMAddonClient addon) {
        this.addon = addon;
        this.widgetDescriptor = new HudWidgetDescriptor() {
            @Override public String getWidgetId() { return "plot_reminder"; }
            @Override public String getDisplayName() { return I18n.get("gm-addon.widget.plot.name"); }
            @Override public boolean isEnabled() { return addon.getConfig().plotWidgetEnabled; }
            @Override public void setEnabled(boolean enabled) {
                addon.getConfig().plotWidgetEnabled = enabled;
                addon.saveConfig();
            }
            @Override public int getX() { return addon.getConfig().plotWidgetX; }
            @Override public int getY() { return addon.getConfig().plotWidgetY; }
            @Override public void setPosition(int x, int y) {
                addon.getConfig().plotWidgetX = x;
                addon.getConfig().plotWidgetY = y;
            }
            @Override public float getScale() { return addon.getConfig().plotWidgetScale; }
            @Override public void setScale(float scale) { addon.getConfig().plotWidgetScale = scale; }
            @Override public int getBaseWidth() { return 240; }
            @Override public int getBaseHeight() { return 34; }
            @Override public void resetToDefault() {
                AddonConfig cfg = addon.getConfig();
                cfg.plotWidgetX = 12;
                cfg.plotWidgetY = 212;
                cfg.plotWidgetScale = 1.0f;
            }
        };
    }

    @Override public String getId() { return "plots"; }
    @Override public String getDisplayName() { return I18n.get("gm-addon.module.plot.name"); }
    @Override public String getDescription() { return I18n.get("gm-addon.module.plot.desc"); }
    @Override public boolean isEnabled() { return addon.getConfig().plotReminderEnabled; }

    @Override
    public void setEnabled(boolean enabled) {
        addon.getConfig().plotReminderEnabled = enabled;
        addon.saveConfig();
    }

    @Override
    public Optional<HudWidgetDescriptor> getHudWidget() {
        return Optional.of(widgetDescriptor);
    }

    // ------------------------------------------------------------------ helpers

    private PlotRepository repo() {
        return addon.getPlotRepository();
    }

    private String accountName() {
        return VersionCompat.getPlayerName(Minecraft.getInstance());
    }

    private List<OwnedPlot> plots() {
        return repo().getPlots(accountName());
    }

    private static void chat(Minecraft mc, String text) {
        if (mc != null && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        }
    }

    public boolean isScanning() {
        return scanState != ScanState.IDLE;
    }

    // ------------------------------------------------------------------ scan

    /** Starts the automatic /menu scan (closes any open screen first). */
    public void startScan() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null || !addon.isActiveOnCurrentServer()) {
            return;
        }
        if (isScanning()) {
            return;
        }
        VersionCompat.setScreen(mc, null);
        scanState = ScanState.WAIT_MENU;
        scanAccum.clear();
        scanPages = 0;
        scanTicks = 0;
        settleTicks = 0;
        lastScreenRef = null;
        chat(mc, I18n.get("gm-addon.chat.plot.scan_started"));
        mc.player.connection.sendCommand("menu");
    }

    private void abortScan(Minecraft mc, String reasonKey) {
        scanState = ScanState.IDLE;
        chat(mc, I18n.get(reasonKey));
    }

    private static String itemText(ItemStack stack) {
        StringBuilder sb = new StringBuilder(stack.getHoverName().getString());
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && lore.lines() != null) {
            for (Component line : lore.lines()) {
                sb.append(" | ").append(line.getString());
            }
        }
        return sb.toString();
    }

    /** True for pagination items like "Nächste Seite" / "Weiter" / "Next page" (never "Zurück"/"Vorherige"). */
    private static boolean isNextPageItem(ItemStack stack) {
        String t = PlotTracker.stripFormatting(itemText(stack)).toLowerCase(Locale.ROOT);
        if (t.contains("zurück") || t.contains("zurueck") || t.contains("vorherige") || t.contains("previous")
            || t.contains("back")) {
            return false;
        }
        return t.contains("nächste") || t.contains("naechste") || t.contains("weiter")
            || t.contains("next") || t.contains("vorwärts") || t.contains("»") || t.contains("→");
    }

    private void tickScan(Minecraft client) {
        scanTicks++;
        if (scanTicks > SCAN_TIMEOUT_TICKS) {
            abortScan(client, "gm-addon.chat.plot.scan_timeout");
            return;
        }
        Screen screen = VersionCompat.getScreen(client);
        if (!(screen instanceof AbstractContainerScreen<?> cs)) {
            lastScreenRef = null;
            return;
        }
        if (screen != lastScreenRef) {
            lastScreenRef = screen;
            settleTicks = 0;
        }
        if (++settleTicks < SETTLE_TICKS) {
            return;
        }
        String title = PlotTracker.stripFormatting(cs.getTitle().getString()).toLowerCase(Locale.ROOT);
        var menu = cs.getMenu();
        if (menu == null || menu.slots == null) {
            return;
        }
        int containerSlots = Math.max(0, menu.slots.size() - 36);

        if (scanState == ScanState.WAIT_MENU && title.contains("spielmen") && !title.contains("region")) {
            for (int i = 0; i < containerSlots; i++) {
                Slot slot = menu.slots.get(i);
                if (slot.hasItem()) {
                    String name = PlotTracker.stripFormatting(slot.getItem().getHoverName().getString()).toLowerCase(Locale.ROOT);
                    if (name.contains("grundst") && name.contains("wohnung")) {
                        if (VersionCompat.clickContainerSlot(client, menu.containerId, slot.index)) {
                            scanState = ScanState.WAIT_REGIONS;
                            lastScreenRef = null;
                            settleTicks = 0;
                        } else {
                            abortScan(client, "gm-addon.chat.plot.scan_failed");
                        }
                        return;
                    }
                }
            }
            abortScan(client, "gm-addon.chat.plot.scan_no_button");
        } else if (scanState == ScanState.WAIT_REGIONS && title.contains("region")) {
            int added = 0;
            Slot nextSlot = null;
            for (int i = 0; i < containerSlots; i++) {
                Slot slot = menu.slots.get(i);
                if (!slot.hasItem()) continue;
                ItemStack stack = slot.getItem();
                OwnedPlot p = PlotTracker.parseMenuItem(stack.getHoverName().getString(), itemText(stack));
                if (p != null) {
                    boolean known = false;
                    for (OwnedPlot k : scanAccum) {
                        if (k.sameAs(p)) { known = true; break; }
                    }
                    if (!known) {
                        scanAccum.add(p);
                        added++;
                    }
                } else if (nextSlot == null && isNextPageItem(stack)) {
                    nextSlot = slot;
                }
            }
            // Multi-page support: keep clicking the "next page" item while new plots show up.
            if (nextSlot != null && added > 0 && ++scanPages < MAX_SCAN_PAGES) {
                if (VersionCompat.clickContainerSlot(client, menu.containerId, nextSlot.index)) {
                    scanTicks = 0;
                    settleTicks = 0;
                    lastScreenRef = screen;
                    return;
                }
            }
            List<OwnedPlot> scanned = new ArrayList<>(scanAccum);
            scanAccum.clear();
            if (client.player != null) {
                client.player.closeContainer();
            }
            scanState = ScanState.IDLE;
            addon.getConfig().plotLastScanEpochMs = System.currentTimeMillis();
            addon.saveConfig();
            if (scanned.isEmpty()) {
                chat(client, I18n.get("gm-addon.chat.plot.scan_empty"));
                return;
            }
            repo().mergeScan(accountName(), scanned);
            chat(client, I18n.get("gm-addon.chat.plot.scan_done", scanned.size()));
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onServerJoin(GMAddonClient addon) {
        joinReminderTicks = JOIN_REMINDER_DELAY_TICKS;
    }

    @Override
    public void onServerDisconnect(GMAddonClient addon) {
        scanState = ScanState.IDLE;
        joinReminderTicks = -1;
    }

    @Override
    public void onClientTick(GMAddonClient addon, Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (isScanning()) {
            tickScan(client);
        }
        if (scanPromptPending && !isScanning() && VersionCompat.getScreen(client) == null) {
            scanPromptPending = false;
            VersionCompat.setScreen(client, new PlotScanPromptScreen(addon));
        }
        if (joinReminderTicks >= 0 && --joinReminderTicks < 0) {
            sendReminder(client, false);
        }
        int intervalMin = addon.getConfig().plotReminderIntervalMinutes;
        if (intervalMin > 0) {
            periodicReminderTicks++;
            if (periodicReminderTicks >= intervalMin * 60 * 20) {
                periodicReminderTicks = 0;
                sendReminder(client, false);
            }
        }
    }

    private boolean scanPromptPending = false;

    public void sendReminder(Minecraft mc, boolean isManualTest) {
        List<OwnedPlot> currentPlots = plots();
        if (currentPlots.isEmpty()) {
            if (isManualTest) {
                chat(mc, I18n.get("gm-addon.chat.plot.test_empty"));
            } else {
                AddonConfig pc = addon.getConfig();
                long sinceScan = System.currentTimeMillis() - pc.plotLastScanEpochMs;
                // Never scanned: always ask. Scanned but nothing found: only ask again sporadically.
                scanPromptPending = pc.plotLastScanEpochMs <= 0L
                    || sinceScan > Math.max(1, pc.plotRescanPromptDays) * 24L * 60L * 60L * 1000L;
            }
            return;
        }
        long now = System.currentTimeMillis();
        int threshold = addon.getConfig().plotReminderDaysBefore;
        List<OwnedPlot> sorted = PlotTracker.sortByDeadline(currentPlots);
        List<String> unknown = new ArrayList<>();
        int warnedCount = 0;
        for (OwnedPlot p : sorted) {
            if (!p.hasBeenVisited()) {
                unknown.add(p.name);
                continue;
            }
            long days = p.daysRemaining(now);
            if (days <= 0) {
                chat(mc, I18n.get("gm-addon.chat.plot.expired", p.name, p.deadlineDate().format(DATE_FORMAT)));
                warnedCount++;
            } else if (days <= threshold) {
                chat(mc, I18n.get("gm-addon.chat.plot.expiring", p.name, days, p.deadlineDate().format(DATE_FORMAT)));
                warnedCount++;
            }
        }
        if (!unknown.isEmpty()) {
            String names = String.join(", ", unknown.subList(0, Math.min(5, unknown.size())))
                + (unknown.size() > 5 ? ", ..." : "");
            chat(mc, I18n.get("gm-addon.chat.plot.never_visited", unknown.size(), names));
            warnedCount += unknown.size();
        }
        if (isManualTest && warnedCount == 0) {
            OwnedPlot next = sorted.get(0);
            if (next.hasBeenVisited()) {
                long d = next.daysRemaining(now);
                chat(mc, I18n.get("gm-addon.chat.plot.test_all_safe", next.name, d, next.deadlineDate().format(DATE_FORMAT)));
            }
        }
    }

    public void simulateTestExpiry() {
        Minecraft mc = Minecraft.getInstance();
        String account = accountName();
        List<OwnedPlot> current = repo().getPlots(account);
        if (current.isEmpty()) {
            OwnedPlot sample = new OwnedPlot("kau2007 (Test)", "Grundstück", 0, 64, 0, "Testgebiet");
            sample.lastVisitedEpochMs = System.currentTimeMillis() - (58L * 24L * 60L * 60L * 1000L); // 2 days left
            repo().mergeScan(account, List.of(sample));
            chat(mc, I18n.get("gm-addon.chat.plot.test_simulated_new", sample.name));
        } else {
            OwnedPlot target = current.get(0);
            target.lastVisitedEpochMs = System.currentTimeMillis() - (58L * 24L * 60L * 60L * 1000L); // 2 days left
            repo().save();
            chat(mc, I18n.get("gm-addon.chat.plot.test_simulated", target.name));
        }
        sendReminder(mc, true);
    }

    public void resetTestExpiry() {
        Minecraft mc = Minecraft.getInstance();
        String account = accountName();
        List<OwnedPlot> current = repo().getPlots(account);
        if (!current.isEmpty()) {
            OwnedPlot target = current.get(0);
            target.lastVisitedEpochMs = System.currentTimeMillis();
            repo().save();
            chat(mc, I18n.get("gm-addon.chat.plot.test_reset", target.name));
        }
        sendReminder(mc, true);
    }

    @Override
    public void onActionBarMessage(GMAddonClient addon, String plainMessage) {
        PlotTracker.OwnerMessage msg = PlotTracker.parseOwnerMessage(plainMessage);
        if (msg == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        AddonConfig cfg = addon.getConfig();
        if (!PlotTracker.isOwnedByUs(msg, accountName(), cfg.plotCompanyName)) {
            return;
        }
        List<OwnedPlot> candidates = PlotTracker.findCandidates(
            plots(), mc.player.getX(), mc.player.getZ(), cfg.plotMatchRadiusBlocks);
        if (candidates.isEmpty()) {
            return;
        }
        OwnedPlot nearest = candidates.get(0);
        long now = System.currentTimeMillis();
        long previous = nearest.lastVisitedEpochMs;
        if (repo().markVisited(accountName(), nearest, now)
            && (previous <= 0L || now - previous > FEEDBACK_MIN_GAP_MS)) {
            chat(mc, I18n.get("gm-addon.chat.plot.visited", nearest.name, nearest.deadlineDate().format(DATE_FORMAT)));
        }
        if (candidates.size() > 1) {
            String key = nearest.name + "@" + nearest.x + "," + nearest.z;
            if (!key.equals(lastHintPlotKey) || now - lastHintMs > HINT_COOLDOWN_MS) {
                lastHintPlotKey = key;
                lastHintMs = now;
                List<String> others = new ArrayList<>();
                for (int i = 1; i < candidates.size(); i++) {
                    others.add(candidates.get(i).name);
                }
                chat(mc, I18n.get("gm-addon.chat.plot.ambiguous", nearest.name, String.join(", ", others)));
            }
        }
    }

    // ------------------------------------------------------------------ HUD

    @Override
    public void renderHudWidget(GMAddonClient addon, GuiGraphicsExtractor graphics, Minecraft mc, float partialTick, boolean editorPreview) {
        AddonConfig cfg = addon.getConfig();
        if (!editorPreview && !cfg.plotWidgetEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        List<OwnedPlot> sorted = PlotTracker.sortByDeadline(plots());

        String line1;
        String line2;
        int accent = 0xFF38BDF8;
        boolean critical = !sorted.isEmpty() && sorted.get(0).hasBeenVisited()
            && sorted.get(0).daysRemaining(now) <= cfg.plotReminderDaysBefore;
        if (!editorPreview && !critical) {
            return; // only show when a plot is below the warning threshold
        }
        if (critical) {
            OwnedPlot p = sorted.get(0);
            long days = p.daysRemaining(now);
            line1 = p.name + " (" + p.type + ")";
            line2 = days <= 0
                ? I18n.get("gm-addon.widget.plot.expired")
                : I18n.get("gm-addon.widget.plot.days_left", days, p.deadlineDate().format(DATE_FORMAT));
            accent = days <= 3 ? 0xFFEF4444 : (days <= cfg.plotReminderDaysBefore ? 0xFFF59E0B : 0xFF22C55E);
        } else if (!sorted.isEmpty()) {
            line1 = I18n.get("gm-addon.widget.plot.unknown_title");
            line2 = I18n.get("gm-addon.widget.plot.unknown_count", sorted.size());
            accent = 0xFFF59E0B;
        } else if (editorPreview) {
            line1 = "kau2007 (Grundstück)";
            line2 = I18n.get("gm-addon.widget.plot.days_left", 12, "01.01.2027 12:00");
        } else {
            return;
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) cfg.plotWidgetX, (float) cfg.plotWidgetY);
        graphics.pose().scale(cfg.plotWidgetScale, cfg.plotWidgetScale);
        graphics.fill(0, 0, 240, 34, 0xD90F172A);
        graphics.outline(0, 0, 240, 34, accent);
        if (mc.font.width(line1) > 228) {
            line1 = mc.font.plainSubstrByWidth(line1, 222) + "...";
        }
        if (mc.font.width(line2) > 228) {
            line2 = mc.font.plainSubstrByWidth(line2, 222) + "...";
        }
        graphics.text(mc.font, line1, 6, 6, 0xFFF8FAFC, true);
        graphics.text(mc.font, line2, 6, 19, accent, false);
        graphics.pose().popMatrix();
    }

    // ------------------------------------------------------------------ settings

    @Override
    public List<Setting<?>> getSettings() {
        AddonConfig cfg = addon.getConfig();
        return List.of(
            new Setting.BooleanSetting(
                "plot_enabled",
                I18n.get("gm-addon.setting.plot.enabled"),
                I18n.get("gm-addon.setting.plot.enabled.desc"),
                () -> cfg.plotReminderEnabled,
                val -> { cfg.plotReminderEnabled = val; addon.saveConfig(); }
            ),
            new Setting.ActionSetting(
                "plot_scan",
                I18n.get("gm-addon.setting.plot.scan"),
                I18n.get("gm-addon.setting.plot.scan.btn"),
                I18n.get("gm-addon.setting.plot.scan.desc"),
                this::startScan
            ),
            new Setting.ActionSetting(
                "plot_overview",
                I18n.get("gm-addon.setting.plot.overview"),
                I18n.get("gm-addon.setting.plot.overview.btn"),
                I18n.get("gm-addon.setting.plot.overview.desc"),
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    VersionCompat.setScreen(mc, new PlotOverviewScreen(addon, VersionCompat.getScreen(mc)));
                }
            ),
            new Setting.StringSetting(
                "plot_company",
                I18n.get("gm-addon.setting.plot.company"),
                I18n.get("gm-addon.setting.plot.company.desc"),
                () -> cfg.plotCompanyName,
                val -> { cfg.plotCompanyName = val == null ? "" : val.trim(); addon.saveConfig(); }
            ),
            new Setting.IntSliderSetting(
                "plot_days_before",
                I18n.get("gm-addon.setting.plot.days_before"),
                I18n.get("gm-addon.setting.plot.days_before.desc"),
                1, 60, " d",
                () -> cfg.plotReminderDaysBefore,
                val -> { cfg.plotReminderDaysBefore = val; addon.saveConfig(); }
            ),
            new Setting.IntSliderSetting(
                "plot_interval",
                I18n.get("gm-addon.setting.plot.interval"),
                I18n.get("gm-addon.setting.plot.interval.desc"),
                0, 120, " min",
                () -> cfg.plotReminderIntervalMinutes,
                val -> { cfg.plotReminderIntervalMinutes = val; addon.saveConfig(); }
            ),
            new Setting.BooleanSetting(
                "plot_widget",
                I18n.get("gm-addon.setting.plot.widget"),
                I18n.get("gm-addon.setting.plot.widget.desc"),
                () -> cfg.plotWidgetEnabled,
                val -> { cfg.plotWidgetEnabled = val; addon.saveConfig(); }
            ),
            new Setting.DoubleSliderSetting(
                "plot_scale",
                I18n.get("gm-addon.setting.plot.scale"),
                I18n.get("gm-addon.setting.plot.scale.desc"),
                0.6, 2.0, 0.05, "x",
                () -> (double) cfg.plotWidgetScale,
                val -> { cfg.plotWidgetScale = val.floatValue(); addon.saveConfig(); }
            )
        );
    }
}
