package de.pflanzenkaktus.enhancedgm.module;

/**
 * Describes a movable and scalable HUD widget provided by a feature module.
 */
public interface HudWidgetDescriptor {
    String getWidgetId();
    String getDisplayName();

    boolean isEnabled();
    void setEnabled(boolean enabled);

    int getX();
    int getY();
    void setPosition(int x, int y);

    float getScale();
    void setScale(float scale);

    int getBaseWidth();
    int getBaseHeight();

    default int getScaledWidth() {
        return Math.round(getBaseWidth() * getScale());
    }

    default int getScaledHeight() {
        return Math.round(getBaseHeight() * getScale());
    }

    default boolean isPointInside(double mx, double my) {
        int x = getX();
        int y = getY();
        return mx >= x - 2 && mx <= x + getScaledWidth() + 2
            && my >= y - 2 && my <= y + getScaledHeight() + 2;
    }

    void resetToDefault();
}
