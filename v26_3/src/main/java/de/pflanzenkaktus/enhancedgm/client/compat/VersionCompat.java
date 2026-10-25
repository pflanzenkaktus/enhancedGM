package de.pflanzenkaktus.enhancedgm.client.compat;

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

    private static boolean overlayFieldsResolved = false;
    private static Field overlayMessageField;
    private static Field overlayTimeField;
    private static Object overlayOwner;

    private static Field findField(Class<?> c, String name) {
        for (; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    /**
     * Returns the currently displayed action bar message component (or null), plus its remaining display
     * time in ticks via {@code outTime[0]}. Works for packets that bypass Fabric's GAME message event
     * (ClientboundSetActionBarTextPacket). Compatible with 26.3 (mc.gui.hud) and 26.1.2 (mc.gui).
     */
    public static net.minecraft.network.chat.Component getActionBarMessage(Minecraft mc, int[] outTime) {
        try {
            if (mc == null || mc.gui == null) return null;
            Object gui = mc.gui;
            Object hud = null;
            try {
                Field hf = gui.getClass().getField("hud");
                hud = hf.get(gui);
            } catch (NoSuchFieldException ignored) {}
            Object[] owners = hud != null ? new Object[] {hud, gui} : new Object[] {gui};
            if (!overlayFieldsResolved || overlayOwner == null || (overlayOwner != gui && overlayOwner != hud)) {
                overlayFieldsResolved = true;
                overlayMessageField = null;
                overlayTimeField = null;
                overlayOwner = null;
                for (Object o : owners) {
                    Field mf = findField(o.getClass(), "overlayMessageString");
                    if (mf != null) {
                        overlayMessageField = mf;
                        overlayTimeField = findField(o.getClass(), "overlayMessageTime");
                        overlayOwner = o;
                        break;
                    }
                }
            }
            if (overlayMessageField == null) return null;
            if (outTime != null && outTime.length > 0 && overlayTimeField != null) {
                outTime[0] = overlayTimeField.getInt(overlayOwner);
            }
            Object v = overlayMessageField.get(overlayOwner);
            return v instanceof net.minecraft.network.chat.Component c ? c : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Performs a left-click (PICKUP) on a slot of the currently open container menu.
     * Compatible with 26.3 (handleContainerInput / ContainerInput) and 26.1.2 (handleInventoryMouseClick / ClickType).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean clickContainerSlot(Minecraft mc, int containerId, int slotIndex) {
        if (mc == null || mc.gameMode == null || mc.player == null) return false;
        try {
            for (Method m : mc.gameMode.getClass().getMethods()) {
                String n = m.getName();
                if ((n.equals("handleContainerInput") || n.equals("handleInventoryMouseClick")) && m.getParameterCount() == 5) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p[0] == int.class && p[1] == int.class && p[2] == int.class && p[3].isEnum()) {
                        Object pickup = Enum.valueOf((Class<Enum>) p[3], "PICKUP");
                        m.invoke(mc.gameMode, containerId, slotIndex, 0, pickup, mc.player);
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Returns the Minecraft account name of the local player.
     */
    public static String getPlayerName(Minecraft mc) {
        try {
            if (mc != null && mc.getUser() != null) {
                return mc.getUser().getName();
            }
        } catch (Throwable ignored) {}
        return "";
    }

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
            // Try 26.3: InputConstants.isKeyDown(int) using SDL scancodes
            try {
                Method m = InputConstants.class.getMethod("isKeyDown", int.class);
                int scancode = switch (keyCode) {
                    case 340 -> 225; // GLFW KEY_LSHIFT -> SDL_SCANCODE_LSHIFT
                    case 344 -> 229; // GLFW KEY_RSHIFT -> SDL_SCANCODE_RSHIFT
                    case 341 -> 224; // GLFW KEY_LCONTROL -> SDL_SCANCODE_LCTRL
                    case 345 -> 228; // GLFW KEY_RCONTROL -> SDL_SCANCODE_RCTRL
                    case 342 -> 226; // GLFW KEY_LALT -> SDL_SCANCODE_LALT
                    case 346 -> 230; // GLFW KEY_RALT -> SDL_SCANCODE_RALT
                    default -> keyCode;
                };
                return (boolean) m.invoke(null, scancode);
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
     * Creates a KeyMapping with the appropriate Key and Type for the running Minecraft version.
     * Uses dynamic lookup of defaultKeyName so it works seamlessly on 26.1.2 (GLFW keycodes)
     * and 26.3+ (modern scancodes).
     */
    public static net.minecraft.client.KeyMapping createKeyMapping(
            String name,
            String defaultKeyName,
            net.minecraft.client.KeyMapping.Category category) {
        InputConstants.Key defaultKey = InputConstants.getKey(defaultKeyName);
        return new net.minecraft.client.KeyMapping(name, defaultKey.getType(), defaultKey.getValue(), category);
    }

    /**
     * Checks if a KeyEvent corresponds to ESCAPE in both 26.1.2 and 26.3+.
     */
    public static boolean isEscape(net.minecraft.client.input.KeyEvent event) {
        if (event == null) return false;
        return event.key() == 256 || event.key() == 41;
    }

    /**
     * Checks if a KeyEvent is Shift in both 26.1.2 and 26.3+.
     */
    public static boolean isShift(net.minecraft.client.input.KeyEvent event) {
        if (event == null) return false;
        return event.hasShiftDown()
                || event.key() == 340 || event.key() == 344
                || event.key() == 225 || event.key() == 229;
    }

    /**
     * Checks if either Left Shift or Right Shift is currently pressed.
     */
    public static boolean isShiftDown() {
        return isKeyDown(340) || isKeyDown(344);
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
