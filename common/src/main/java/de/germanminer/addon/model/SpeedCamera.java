package de.germanminer.addon.model;

public record SpeedCamera(
    String id,
    String name,
    double x,
    double y,
    double z,
    int speedLimitKmh,
    Float facingYaw,
    double triggerRadius,
    boolean autoAdded,
    String addedBy,
    long createdAtEpochMs
) {
    /**
     * Convenience constructor for manually defined / legacy speed cameras (`autoAdded = false`).
     */
    public SpeedCamera(
        String id,
        String name,
        double x,
        double y,
        double z,
        int speedLimitKmh,
        Float facingYaw,
        double triggerRadius
    ) {
        this(id, name, x, y, z, speedLimitKmh, facingYaw, triggerRadius, false, "manual", 0L);
    }

    public int effectiveSpeedLimit() {
        return speedLimitKmh > 0 ? speedLimitKmh : 70;
    }

    public double horizontalDistanceTo(double px, double pz) {
        double dx = this.x - px;
        double dz = this.z - pz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double distance3dTo(double px, double py, double pz) {
        double dx = this.x - px;
        double dy = this.y - py;
        double dz = this.z - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
