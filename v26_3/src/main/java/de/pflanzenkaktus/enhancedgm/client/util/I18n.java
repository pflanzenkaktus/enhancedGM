package de.pflanzenkaktus.enhancedgm.client.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * Localization system for enhancedGM.
 * Backed by standard Minecraft/Fabric JSON language files (assets/gm-addon/lang/de_de.json and en_us.json).
 * Supports AddonConfig.language overrides ("AUTO", "EN", "DE") as well as native Minecraft language selection.
 */
public final class I18n {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static final Map<String, String> DE_MAP = loadLangMap("de_de");
    private static final Map<String, String> EN_MAP = loadLangMap("en_us");

    private I18n() {}

    private static Map<String, String> loadLangMap(String langCode) {
        String resourcePath = "/assets/gm-addon/lang/" + langCode + ".json";
        try (InputStream in = I18n.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    Map<String, String> map = GSON.fromJson(reader, MAP_TYPE);
                    if (map != null) {
                        return map;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[enhancedGM] Failed to load language resource: " + resourcePath + " - " + e.getMessage());
        }
        return Collections.emptyMap();
    }

    public static boolean isEnglish() {
        GMAddonClient addon = GMAddonClient.getInstance();
        if (addon != null && addon.getConfig() != null) {
            String cfgLang = addon.getConfig().language;
            if ("EN".equalsIgnoreCase(cfgLang)) {
                return true;
            }
            if ("DE".equalsIgnoreCase(cfgLang)) {
                return false;
            }
        }
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.getLanguageManager() != null) {
                String mcLang = mc.getLanguageManager().getSelected();
                if (mcLang != null && !mcLang.toLowerCase().startsWith("de")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Resolves a localized string by translation key and formats it if arguments are provided.
     */
    public static String get(String key, Object... args) {
        GMAddonClient addon = GMAddonClient.getInstance();
        String cfgLang = (addon != null && addon.getConfig() != null) ? addon.getConfig().language : "AUTO";

        if ("EN".equalsIgnoreCase(cfgLang)) {
            String val = EN_MAP.get(key);
            if (val != null) {
                return formatSafely(val, args);
            }
        } else if ("DE".equalsIgnoreCase(cfgLang)) {
            String val = DE_MAP.get(key);
            if (val != null) {
                return formatSafely(val, args);
            }
        } else {
            // "AUTO" -> Prefer Minecraft's native client I18n if available
            try {
                if (net.minecraft.locale.Language.getInstance().has(key)) {
                    return net.minecraft.client.resources.language.I18n.get(key, args);
                }
            } catch (Exception ignored) {
            }
            // Fallback depending on client language
            Map<String, String> fallback = isEnglish() ? EN_MAP : DE_MAP;
            String val = fallback.get(key);
            if (val != null) {
                return formatSafely(val, args);
            }
        }

        // Final fallback: check EN then DE then return key
        String fallbackVal = EN_MAP.get(key);
        if (fallbackVal == null) {
            fallbackVal = DE_MAP.get(key);
        }
        if (fallbackVal != null) {
            return formatSafely(fallbackVal, args);
        }
        return key;
    }

    /**
     * Resolves a Component for the given translation key.
     */
    public static Component component(String key, Object... args) {
        GMAddonClient addon = GMAddonClient.getInstance();
        String cfgLang = (addon != null && addon.getConfig() != null) ? addon.getConfig().language : "AUTO";
        if ("EN".equalsIgnoreCase(cfgLang) || "DE".equalsIgnoreCase(cfgLang)) {
            return Component.literal(get(key, args));
        }
        return Component.translatable(key, args);
    }

    /**
     * Convenience alias for get(key, args).
     */
    public static String tr(String key, Object... args) {
        return get(key, args);
    }

    private static String formatSafely(String pattern, Object... args) {
        if (args == null || args.length == 0) {
            return pattern;
        }
        try {
            return String.format(pattern, args);
        } catch (Exception e) {
            return pattern;
        }
    }
}

