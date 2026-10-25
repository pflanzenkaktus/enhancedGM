package de.pflanzenkaktus.enhancedgm.feature;

import de.pflanzenkaktus.enhancedgm.model.OwnedPlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java logic for the plot-expiry reminder: parsing the server's menu tooltips and
 * "Dieses Grundstück ist im Besitz von X" action bar messages, ownership checks and plot matching.
 */
public final class PlotTracker {
    private static final Pattern FORMAT_CODES = Pattern.compile("§.");
    private static final Pattern OWNER_PATTERN =
        Pattern.compile("(?i)\\bist\\s+im\\s+Besitz\\s+von\\s+(.+?)\\s*$");
    private static final Pattern COMPANY_SUFFIX = Pattern.compile("(?i)\\s*\\((?:Firma)\\)\\s*$");
    private static final Pattern COORD_PATTERN =
        Pattern.compile("(?i)x:\\s*(-?\\d+)\\s*y:\\s*(-?\\d+)\\s*z:\\s*(-?\\d+)");
    private static final Pattern TYPE_PATTERN = Pattern.compile("(?i)Typ:\\s*([^|]+?)\\s*(?:\\||$)");
    private static final Pattern AREA_PATTERN = Pattern.compile("(?i)Stadtgebiet:\\s*([^|]+?)\\s*(?:\\||$)");

    private PlotTracker() {}

    /** Owner as announced on the action bar. */
    public record OwnerMessage(String ownerName, boolean company) {}

    public static String stripFormatting(String s) {
        return s == null ? "" : FORMAT_CODES.matcher(s).replaceAll("").trim();
    }

    /**
     * Parses e.g. "Diese Mietzone ist im Besitz von SuperChaos (Firma)" or
     * "Dieses Grundstück ist im Besitz von PflanzenKaktus". Returns null if the text is not an ownership message.
     */
    public static OwnerMessage parseOwnerMessage(String rawMessage) {
        String plain = stripFormatting(rawMessage);
        Matcher m = OWNER_PATTERN.matcher(plain);
        if (!m.find()) {
            return null;
        }
        String owner = m.group(1).trim();
        boolean company = false;
        Matcher cm = COMPANY_SUFFIX.matcher(owner);
        if (cm.find()) {
            company = true;
            owner = owner.substring(0, cm.start()).trim();
        }
        if (owner.isEmpty()) {
            return null;
        }
        return new OwnerMessage(owner, company);
    }

    /**
     * A message counts only if the owner is the real player (non-company message) or the configured company
     * name (company message).
     */
    public static boolean isOwnedByUs(OwnerMessage msg, String playerName, String companyName) {
        if (msg == null) {
            return false;
        }
        String expected = msg.company() ? companyName : playerName;
        return expected != null && !expected.isBlank() && expected.trim().equalsIgnoreCase(msg.ownerName());
    }

    /**
     * Parses a region item from the menu. {@code itemText} is the item name followed by its lore lines, joined by " | ".
     * Returns null if the text does not contain coordinates.
     */
    public static OwnedPlot parseMenuItem(String itemName, String itemText) {
        String name = stripFormatting(itemName);
        String text = stripFormatting(itemText).replace('—', '-');
        Matcher cm = COORD_PATTERN.matcher(text);
        if (name.isEmpty() || !cm.find()) {
            return null;
        }
        String type = "";
        Matcher tm = TYPE_PATTERN.matcher(text);
        if (tm.find()) {
            type = tm.group(1).trim();
        }
        String area = "";
        Matcher am = AREA_PATTERN.matcher(text);
        if (am.find()) {
            area = am.group(1).trim();
        }
        return new OwnedPlot(name, type,
            Integer.parseInt(cm.group(1)), Integer.parseInt(cm.group(2)), Integer.parseInt(cm.group(3)), area);
    }

    /** All plots within {@code radius} blocks horizontally, nearest first. */
    public static List<OwnedPlot> findCandidates(List<OwnedPlot> plots, double px, double pz, double radius) {
        List<OwnedPlot> result = new ArrayList<>();
        for (OwnedPlot p : plots) {
            if (p.horizontalDistanceTo(px, pz) <= radius) {
                result.add(p);
            }
        }
        result.sort(Comparator.comparingDouble(p -> p.horizontalDistanceTo(px, pz)));
        return result;
    }

    /** Plots sorted by urgency: expired/soonest first, never-visited plots last. */
    public static List<OwnedPlot> sortByDeadline(List<OwnedPlot> plots) {
        List<OwnedPlot> sorted = new ArrayList<>(plots);
        sorted.sort(Comparator.comparingLong((OwnedPlot p) -> p.hasBeenVisited() ? p.deadlineEpochMs() : Long.MAX_VALUE)
            .thenComparing(p -> p.name.toLowerCase(Locale.ROOT)));
        return sorted;
    }
}
