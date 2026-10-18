package de.germanminer.addon.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.germanminer.addon.client.GMAddonClient;
import de.germanminer.addon.feature.DamageLogTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Interactive Damage-Log & 30s Notwehr Reviewer Screen.
 * Displays per-opponent Notwehr verdicts and a chronological hit-by-hit breakdown
 * of GermanMiner's {@code /damagelog} output.
 */
public class DamageLogScreen extends Screen {
    private final GMAddonClient addon;
    private final Screen parent;

    private String selectedOpponentFilter = null; // null = All opponents
    private double scrollOffset = 0.0;
    private int maxScroll = 0;

    public DamageLogScreen(GMAddonClient addon, Screen parent) {
        super(Component.literal("GermanMiner — Damage-Log & Notwehr-Prüfer"));
        this.addon = addon;
        this.parent = parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        DamageLogTracker tracker = addon.getDamageLogTracker();

        // Dark backdrop
        graphics.fill(0, 0, this.width, this.height, 0xDF0B0F17);

        // Top Header Bar
        graphics.fill(0, 0, this.width, 36, 0xF2121824);
        graphics.fill(0, 35, this.width, 36, 0xFF3B82F6);

        graphics.text(mc.font, "§b§lGM-Addon §8| §f§lDamage-Log & 30s Notwehr-Prüfer", 14, 9, 0xFFFFFFFF, true);

        String owner = tracker.getLogOwnerName();
        String subTitle = owner.isBlank()
            ? String.format(Locale.ROOT, "§7Notwehr-Regel: §e%ds §7nach erlittenem Treffer straffrei", addon.getConfig().notwehrWindowSeconds)
            : String.format(Locale.ROOT, "§7Spieler: §f%s §8| §7Notwehr-Limit: §e%ds", owner, addon.getConfig().notwehrWindowSeconds);
        graphics.text(mc.font, subTitle, 14, 21, 0xFFCBD5E1, false);

        // Live Notwehr timer badge in header if active
        if (tracker.isNotwehrTimerActive()) {
            String liveBadge = String.format(
                Locale.ROOT,
                "⚔ NOTWEHR AKTIV (%s): %.1fs",
                tracker.getActiveAttackerName(),
                tracker.getRemainingNotwehrSeconds()
            );
            int badgeW = mc.font.width(liveBadge) + 14;
            int badgeX = (this.width / 2) - (badgeW / 2);
            graphics.fill(badgeX, 8, badgeX + badgeW, 28, 0xEE065F46);
            drawBorder(graphics, badgeX, 8, badgeW, 20, 0xFF10B981);
            graphics.text(mc.font, liveBadge, badgeX + 7, 14, 0xFFA7F3D0, true);
        }

        // Top-right action buttons
        int closeW = 72;
        int closeX = this.width - closeW - 12;
        boolean closeHover = inBounds(mouseX, mouseY, closeX, 7, closeW, 22);
        graphics.fill(closeX, 7, closeX + closeW, 29, closeHover ? 0xFF2563EB : 0xFF1D4ED8);
        drawBorder(graphics, closeX, 7, closeW, 22, 0xFF60A5FA);
        String closeLbl = "Schließen";
        graphics.text(mc.font, closeLbl, closeX + (closeW - mc.font.width(closeLbl)) / 2, 14, 0xFFFFFFFF, false);

        int cmdW = 148;
        int cmdX = closeX - cmdW - 8;
        boolean cmdHover = inBounds(mouseX, mouseY, cmdX, 7, cmdW, 22);
        graphics.fill(cmdX, 7, cmdX + cmdW, 29, cmdHover ? 0xFF047857 : 0xFF065F46);
        drawBorder(graphics, cmdX, 7, cmdW, 22, 0xFF34D399);
        String cmdLbl = "/damagelog abrufen";
        graphics.text(mc.font, cmdLbl, cmdX + (cmdW - mc.font.width(cmdLbl)) / 2, 14, 0xFFECFDF5, false);

        List<DamageLogTracker.OpponentSummary> summaries = tracker.getOpponentSummaries();
        List<DamageLogTracker.DamageLogEntry> allEntries = tracker.getEvaluatedEntries();

        if (allEntries.isEmpty()) {
            renderEmptyState(graphics, mc, mouseX, mouseY);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // Opponent Filter Pills Row (Y = 44..66)
        int filterX = 14;
        int filterY = 42;
        String allPillText = "Alle Gegner (" + allEntries.size() + ")";
        int allPillW = mc.font.width(allPillText) + 16;
        boolean allSelected = (selectedOpponentFilter == null);
        boolean allHover = inBounds(mouseX, mouseY, filterX, filterY, allPillW, 20);
        graphics.fill(filterX, filterY, filterX + allPillW, filterY + 20, allSelected ? 0xFF1E3A8A : (allHover ? 0xFF1E293B : 0xFF0F172A));
        drawBorder(graphics, filterX, filterY, allPillW, 20, allSelected ? 0xFF60A5FA : 0xFF334155);
        graphics.text(mc.font, allPillText, filterX + 8, filterY + 6, allSelected ? 0xFFFFFFFF : 0xFF94A3B8, false);
        filterX += allPillW + 6;

        for (DamageLogTracker.OpponentSummary sum : summaries) {
            String pillText = sum.opponentName() + " (" + (sum.incomingHits() + sum.outgoingHits()) + ")";
            int pillW = mc.font.width(pillText) + 18;
            boolean sel = sum.opponentName().equalsIgnoreCase(selectedOpponentFilter);
            boolean hov = inBounds(mouseX, mouseY, filterX, filterY, pillW, 20);
            int statusCol = getStatusBorderColor(sum.overallStatus());
            graphics.fill(filterX, filterY, filterX + pillW, filterY + 20, sel ? 0xFF1E293B : (hov ? 0xFF162032 : 0xFF0F172A));
            drawBorder(graphics, filterX, filterY, pillW, 20, sel ? 0xFFFFFFFF : statusCol);
            graphics.text(mc.font, pillText, filterX + 9, filterY + 6, sel ? 0xFFFFFFFF : 0xFFE2E8F0, false);
            filterX += pillW + 6;
            if (filterX > this.width - 120) break;
        }

        // Selected Opponent Summary Banner (Y = 68..112)
        int bannerY = 68;
        int bannerH = 44;
        DamageLogTracker.OpponentSummary activeSummary = getDisplayedSummary(summaries);
        if (activeSummary != null) {
            int borderCol = getStatusBorderColor(activeSummary.overallStatus());
            int bgCol = getStatusBgColor(activeSummary.overallStatus());
            graphics.fill(14, bannerY, this.width - 14, bannerY + bannerH, bgCol);
            drawBorder(graphics, 14, bannerY, this.width - 28, bannerH, borderCol);

            String badge = getStatusShortBadge(activeSummary.overallStatus());
            graphics.text(mc.font, "§l" + badge + " §r§f— " + activeSummary.summaryVerdict(), 22, bannerY + 8, 0xFFFFFFFF, true);

            String statsLine = String.format(
                Locale.ROOT,
                "§7Gegner: §f%s   §8|   §cErlitten: %dx §7(zuletzt %s)   §8|   §aZugefügt: %dx §7(zuletzt %s)   §8|   §eNotwehr galt bis: %s",
                activeSummary.opponentName(),
                activeSummary.incomingHits(),
                activeSummary.lastIncomingTime(),
                activeSummary.outgoingHits(),
                activeSummary.lastOutgoingTime(),
                activeSummary.notwehrValidUntilTime()
            );
            graphics.text(mc.font, statsLine, 22, bannerY + 24, 0xFFCBD5E1, false);
        }

        // Chronological Entries List (Y = 120 .. height - 16)
        int listTop = 120;
        int listBottom = this.height - 14;
        List<DamageLogTracker.DamageLogEntry> filteredEntries = new ArrayList<>();
        for (DamageLogTracker.DamageLogEntry e : allEntries) {
            if (selectedOpponentFilter == null || e.opponent().equalsIgnoreCase(selectedOpponentFilter)) {
                filteredEntries.add(e);
            }
        }

        int rowHeight = 32;
        int contentHeight = filteredEntries.size() * rowHeight + 8;
        this.maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
        this.scrollOffset = Math.clamp(this.scrollOffset, 0.0, this.maxScroll);

        int y = listTop - (int) this.scrollOffset;
        for (int i = 0; i < filteredEntries.size(); i++) {
            DamageLogTracker.DamageLogEntry entry = filteredEntries.get(i);
            if (y + rowHeight >= listTop && y <= listBottom) {
                renderEntryRow(graphics, mc, entry, i + 1, 14, y, this.width - 28, rowHeight - 4);
            }
            y += rowHeight;
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderEntryRow(
        GuiGraphicsExtractor graphics,
        Minecraft mc,
        DamageLogTracker.DamageLogEntry entry,
        int index,
        int x,
        int y,
        int width,
        int height
    ) {
        boolean isIncoming = entry.direction() == DamageLogTracker.Direction.INCOMING;
        int borderCol = getStatusBorderColor(entry.status());
        int rowBg = isIncoming ? 0xD81E141B : 0xD8111F1C;

        graphics.fill(x, y, x + width, y + height, rowBg);
        drawBorder(graphics, x, y, width, height, borderCol);
        // Left color stripe
        graphics.fill(x, y, x + 4, y + height, borderCol);

        // Timestamp + Direction pill
        String timeAndDir = isIncoming
            ? "§7" + entry.timestamp() + "  §c● ERLITTEN"
            : "§7" + entry.timestamp() + "  §a● ZUGEFÜGT";
        graphics.text(mc.font, timeAndDir, x + 10, y + 5, 0xFFFFFFFF, true);

        // Attacker » Victim + Hover/DMG detail
        String pairText = isIncoming
            ? "§c" + entry.attacker() + " §7» §f" + entry.victim()
            : "§a" + entry.attacker() + " §7» §f" + entry.victim();
        if (entry.hoverDetail() != null && !entry.hoverDetail().isBlank()) {
            pairText += " §8[" + entry.hoverDetail() + "]";
        }
        graphics.text(mc.font, pairText, x + 150, y + 5, 0xFFFFFFFF, true);

        // Right-aligned status badge
        String badge = getEntryBadgeText(entry);
        int badgeColor = getStatusTextColor(entry.status());
        int badgeW = mc.font.width(badge);
        graphics.text(mc.font, badge, x + width - badgeW - 10, y + 5, badgeColor, true);

        // Bottom explanation line
        graphics.text(mc.font, entry.statusExplanation(), x + 10, y + 16, 0xFFCBD5E1, false);
    }

    private void renderEmptyState(GuiGraphicsExtractor graphics, Minecraft mc, int mouseX, int mouseY) {
        int boxW = Math.min(460, this.width - 40);
        int boxH = 128;
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;

        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xEE111827);
        drawBorder(graphics, boxX, boxY, boxW, boxH, 0xFF334155);

        String title = "§eNoch kein Damage-Log in dieser Sitzung geladen";
        graphics.text(mc.font, title, boxX + (boxW - mc.font.width(title)) / 2, boxY + 18, 0xFFFFFFFF, true);

        String sub1 = "Klicke unten auf §a/damagelog auf Server ausführen§7 oder gib im Chat";
        String sub2 = "§f/damagelog§7 ein — das Addon analysiert automatisch alle Treffer auf";
        String sub3 = String.format(Locale.ROOT, "die §e%d-Sekunden Notwehr-Regel§7!", addon.getConfig().notwehrWindowSeconds);
        graphics.text(mc.font, sub1, boxX + (boxW - mc.font.width(sub1)) / 2, boxY + 38, 0xFFCBD5E1, false);
        graphics.text(mc.font, sub2, boxX + (boxW - mc.font.width(sub2)) / 2, boxY + 50, 0xFFCBD5E1, false);
        graphics.text(mc.font, sub3, boxX + (boxW - mc.font.width(sub3)) / 2, boxY + 62, 0xFFCBD5E1, false);

        int btnW = 230;
        int btnH = 24;
        int btnX = boxX + (boxW - btnW) / 2;
        int btnY = boxY + 86;
        boolean hov = inBounds(mouseX, mouseY, btnX, btnY, btnW, btnH);
        graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, hov ? 0xFF059669 : 0xFF047857);
        drawBorder(graphics, btnX, btnY, btnW, btnH, 0xFF34D399);
        String btnTxt = "/damagelog auf Server ausführen";
        graphics.text(mc.font, btnTxt, btnX + (btnW - mc.font.width(btnTxt)) / 2, btnY + 8, 0xFFFFFFFF, true);
    }

    private DamageLogTracker.OpponentSummary getDisplayedSummary(List<DamageLogTracker.OpponentSummary> summaries) {
        if (summaries.isEmpty()) return null;
        if (selectedOpponentFilter != null) {
            for (DamageLogTracker.OpponentSummary s : summaries) {
                if (s.opponentName().equalsIgnoreCase(selectedOpponentFilter)) {
                    return s;
                }
            }
        }
        return summaries.get(summaries.size() - 1);
    }

    private String getEntryBadgeText(DamageLogTracker.DamageLogEntry entry) {
        return switch (entry.status()) {
            case INCOMING_ATTACK -> "Notwehr bis " + entry.notwehrValidUntilTime();
            case VALID_NOTWEHR -> "✔ NOTWEHR (" + entry.deltaSecondsFromIncoming() + "s <= " + addon.getConfig().notwehrWindowSeconds + "s)";
            case EXPIRED_NOTWEHR -> "⚠ ZU SPÄT (" + entry.deltaSecondsFromIncoming() + "s > " + addon.getConfig().notwehrWindowSeconds + "s)";
            case FIRST_STRIKE_BY_SELF -> "✖ KEINE NOTWEHR (Erstschlag)";
        };
    }

    private String getStatusShortBadge(DamageLogTracker.NotwehrStatus status) {
        return switch (status) {
            case VALID_NOTWEHR -> "§a✔ NOTWEHR LEGITIM";
            case INCOMING_ATTACK -> "§bℹ ANGEGRIFFEN (KEINE GEGENWEHR)";
            case EXPIRED_NOTWEHR -> "§6⚠ NOTWEHR-ZEIT ÜBERSCHRITTEN";
            case FIRST_STRIKE_BY_SELF -> "§c✖ KEINE NOTWEHR (ERSTSCHLAG)";
        };
    }

    private int getStatusBorderColor(DamageLogTracker.NotwehrStatus status) {
        return switch (status) {
            case VALID_NOTWEHR -> 0xFF10B981;
            case INCOMING_ATTACK -> 0xFF38BDF8;
            case EXPIRED_NOTWEHR -> 0xFFF59E0B;
            case FIRST_STRIKE_BY_SELF -> 0xFFEF4444;
        };
    }

    private int getStatusBgColor(DamageLogTracker.NotwehrStatus status) {
        return switch (status) {
            case VALID_NOTWEHR -> 0xE6064E3B;
            case INCOMING_ATTACK -> 0xE60C4A6E;
            case EXPIRED_NOTWEHR -> 0xE678350F;
            case FIRST_STRIKE_BY_SELF -> 0xE67F1D1D;
        };
    }

    private int getStatusTextColor(DamageLogTracker.NotwehrStatus status) {
        return switch (status) {
            case VALID_NOTWEHR -> 0xFF34D399;
            case INCOMING_ATTACK -> 0xFF7DD3FC;
            case EXPIRED_NOTWEHR -> 0xFFFBBF24;
            case FIRST_STRIKE_BY_SELF -> 0xFFF87171;
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int btn = event.button();
        if (btn != 0 && btn != 1) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = event.x();
        double my = event.y();
        Minecraft mc = Minecraft.getInstance();

        int closeW = 72;
        int closeX = this.width - closeW - 12;
        if (inBounds(mx, my, closeX, 7, closeW, 22)) {
            onClose();
            return true;
        }

        int cmdW = 148;
        int cmdX = closeX - cmdW - 8;
        if (inBounds(mx, my, cmdX, 7, cmdW, 22)) {
            requestServerDamageLog();
            return true;
        }

        DamageLogTracker tracker = addon.getDamageLogTracker();
        List<DamageLogTracker.DamageLogEntry> allEntries = tracker.getEvaluatedEntries();
        if (allEntries.isEmpty()) {
            int boxW = Math.min(460, this.width - 40);
            int boxH = 128;
            int boxX = (this.width - boxW) / 2;
            int boxY = (this.height - boxH) / 2;
            int btnW = 230;
            int btnH = 24;
            int btnX = boxX + (boxW - btnW) / 2;
            int btnY = boxY + 86;
            if (inBounds(mx, my, btnX, btnY, btnW, btnH)) {
                requestServerDamageLog();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        // Check filter pills
        int filterX = 14;
        int filterY = 42;
        String allPillText = "Alle Gegner (" + allEntries.size() + ")";
        int allPillW = mc.font.width(allPillText) + 16;
        if (inBounds(mx, my, filterX, filterY, allPillW, 20)) {
            selectedOpponentFilter = null;
            scrollOffset = 0.0;
            return true;
        }
        filterX += allPillW + 6;

        for (DamageLogTracker.OpponentSummary sum : tracker.getOpponentSummaries()) {
            String pillText = sum.opponentName() + " (" + (sum.incomingHits() + sum.outgoingHits()) + ")";
            int pillW = mc.font.width(pillText) + 18;
            if (inBounds(mx, my, filterX, filterY, pillW, 20)) {
                selectedOpponentFilter = sum.opponentName();
                scrollOffset = 0.0;
                return true;
            }
            filterX += pillW + 6;
            if (filterX > this.width - 120) break;
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void requestServerDamageLog() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand("damagelog");
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            this.scrollOffset = Math.clamp(this.scrollOffset - scrollY * 22.0, 0.0, this.maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }
}
