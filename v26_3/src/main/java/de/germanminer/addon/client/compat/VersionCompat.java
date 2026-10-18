package de.germanminer.addon.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Universal compatibility bridge supporting Minecraft versions 26.1.2 through 26.3+.
 * Dynamically resolves renamed GUI and Input methods between minor revisions.
 */
public final class VersionCompat {

    private VersionCompat() {}

    /**
     * Opens or switches to a GUI Screen.
     * Compatible with 26.3 (mc.gui.setScreen) and 26.1.2 (mc.setScreen).
     */
    public static void setScreen(Minecraft mc, Screen screen) {
        if (mc == null) return;
        try {
            // In 26.3: mc.gui.setScreen(Screen)
            Object gui = mc.gui;
            if (gui != null) {
                try {
                    Method m = gui.getClass().getMethod("setScreen", Screen.class);
                    m.invoke(gui, screen);
                    return;
                } catch (NoSuchMethodException ignored) {}
            }

            // In 26.1.2: mc.setScreen(Screen)
            Method m = mc.getClass().getMethod("setScreen", Screen.class);
            m.invoke(mc, screen);
        } catch (Throwable t) {
            // Fallback: direct reflection on field if needed
            try {
                Field f = mc.getClass().getField("screen");
                f.set(mc, screen);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Returns the currently open Screen, or null if no screen is active.
     * Compatible with 26.3 (mc.gui.screen()) and 26.1.2 (mc.screen).
     */
    public static Screen getScreen(Minecraft mc) {
        if (mc == null) return null;
        try {
            // In 26.3: mc.gui.screen()
            Object gui = mc.gui;
            if (gui != null) {
                try {
                    Method m = gui.getClass().getMethod("screen");
                    Object res = m.invoke(gui);
                    if (res instanceof Screen s) return s;
                    if (res == null) return null;
                } catch (NoSuchMethodException ignored) {}
            }

            // In 26.1.2: mc.screen (field or getter)
            try {
                Field f = mc.getClass().getField("screen");
                Object res = f.get(mc);
                if (res instanceof Screen s) return s;
            } catch (NoSuchFieldException ignored) {}

            try {
                Method m = mc.getClass().getMethod("screen");
                Object res = m.invoke(mc);
                if (res instanceof Screen s) return s;
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Checks if a specific key is pressed down on the keyboard.
     * Compatible with 26.3 (InputConstants.isKeyDown(int)) and 26.1.2 (InputConstants.isKeyDown(Window/long, int)).
     */
    public static boolean isKeyDown(int keyCode) {
        try {
            // Try 26.3: InputConstants.isKeyDown(int)
            try {
                Method m = InputConstants.class.getMethod("isKeyDown", int.class);
                return (boolean) m.invoke(null, keyCode);
            } catch (NoSuchMethodException ignored) {}

            // Try 26.1.2: InputConstants.isKeyDown(Window/long, int)
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;
            Object window = mc.getWindow();
            if (window == null) return false;

            for (Method m : InputConstants.class.getMethods()) {
                if (m.getName().equals("isKeyDown") && m.getParameterCount() == 2) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params[1] == int.class) {
                        if (params[0].isInstance(window)) {
                            return (boolean) m.invoke(null, window, keyCode);
                        } else if (params[0] == long.class) {
                            try {
                                Method handleM = window.getClass().getMethod("handle");
                                long handle = (long) handleM.invoke(window);
                                return (boolean) m.invoke(null, handle, keyCode);
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Checks if either Left Shift or Right Shift is currently pressed.
     */
    public static boolean isShiftDown() {
        return isKeyDown(InputConstants.KEY_LSHIFT) || isKeyDown(InputConstants.KEY_RSHIFT);
    }

    /**
     * Returns the BossHealthOverlay from the HUD.
     * Compatible with 26.3 (mc.gui.hud.getBossOverlay()) and 26.1.2 (mc.gui.getBossOverlay()).
     */
    public static BossHealthOverlay getBossOverlay(Minecraft mc) {
        if (mc == null || mc.gui == null) return null;
        try {
            Object gui = mc.gui;

            // In 26.3: mc.gui.hud.getBossOverlay()
            try {
                Field hudField = gui.getClass().getField("hud");
                Object hud = hudField.get(gui);
                if (hud != null) {
                    Method m = hud.getClass().getMethod("getBossOverlay");
                    return (BossHealthOverlay) m.invoke(hud);
                }
            } catch (NoSuchFieldException ignored) {}

            // In 26.1.2: mc.gui.getBossOverlay()
            try {
                Method m = gui.getClass().getMethod("getBossOverlay");
                return (BossHealthOverlay) m.invoke(gui);
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}
        return null;
    }
}
