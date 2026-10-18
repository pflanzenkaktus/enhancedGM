package de.germanminer.addon.model;

public record BlueMapMarker(
    String id,
    String categoryId,
    String categoryLabel,
    String label,
    String detail,
    double x,
    double y,
    double z,
    int colorArgb
) {}
