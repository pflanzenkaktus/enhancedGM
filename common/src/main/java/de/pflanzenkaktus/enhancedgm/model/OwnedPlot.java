package de.pflanzenkaktus.enhancedgm.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

/**
 * A rented / bought plot ("Grundstück", "Mietzone", "Wohnung") owned by the player,
 * as listed in /menu -> "Grundstücke &amp; Wohnungen".
 */
public class OwnedPlot {
    /** Duration after which an un-entered plot is cleared by the server. */
    public static final int EXPIRY_DAYS = 60;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    public String name = "";
    public String type = "";
    public int x;
    public int y;
    public int z;
    public String area = "";
    /** Epoch millis of the last verified visit, or 0 if the plot has never been entered since scanning. */
    public long lastVisitedEpochMs = 0L;

    public OwnedPlot() {}

    public OwnedPlot(String name, String type, int x, int y, int z, String area) {
        this.name = name;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.area = area == null ? "" : area;
    }

    public boolean hasBeenVisited() {
        return lastVisitedEpochMs > 0L;
    }

    /** Epoch millis of the latest moment the plot has to be entered again, or -1 if unknown. */
    public long deadlineEpochMs() {
        return hasBeenVisited() ? lastVisitedEpochMs + EXPIRY_DAYS * DAY_MS : -1L;
    }

    /** Remaining whole days (rounded up; may be &lt;= 0 when expired), or {@link Long#MAX_VALUE} if unknown. */
    public long daysRemaining(long nowEpochMs) {
        if (!hasBeenVisited()) {
            return Long.MAX_VALUE;
        }
        long remainingMs = deadlineEpochMs() - nowEpochMs;
        return Math.floorDiv(remainingMs + DAY_MS - 1L, DAY_MS);
    }

    public LocalDateTime deadlineDate() {
        if (!hasBeenVisited()) {
            return null;
        }
        return Instant.ofEpochMilli(deadlineEpochMs()).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public double horizontalDistanceTo(double px, double pz) {
        return Math.hypot(px - x, pz - z);
    }

    public boolean sameAs(OwnedPlot other) {
        return other != null
            && name.equalsIgnoreCase(other.name)
            && x == other.x && y == other.y && z == other.z;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s (%s) @ %d, %d, %d", name, type, x, y, z);
    }
}
