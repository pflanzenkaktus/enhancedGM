# How to Create Plugins / Feature Modules (`PLUGIN_AND_MODULE_GUIDE.md`)

The **GermanMiner Addon (`gm-addon`)** is built around a **Plugin / Feature Module Architecture**. Every feature in the addon (Minimap, Tacho, Blitzer-Warnung, Spawnschutz, Damage-Log) is a self-contained module implementing [`ClientFeatureModule`](../v26_3/src/main/java/de/germanminer/addon/client/module/ClientFeatureModule.java).

When you register a new module in [`ModuleManager`](../v26_3/src/main/java/de/germanminer/addon/client/module/ModuleManager.java):
1. **Settings UI (`O` & Mod Menu)** automatically creates a new category tab in the left sidebar and generates interactive sliders, toggles, cycle buttons, text boxes, and action buttons for all `Setting<?>` items returned by your module.
2. **HUD Layout Editor (`H`)** automatically discovers your module's `HudWidgetDescriptor` (if present) so users can drag it with the mouse, resize it with the scroll wheel, toggle it with right-click, and reset its position.
3. **Lifecycle Events** (`onInit`, `onServerJoin`, `onServerDisconnect`, `onClientTick`, `onPlayerRespawn`, `onChatMessage`, `onActionBarMessage`, `renderWorldOverlay`, `renderHudWidget`) are automatically dispatched to your module.

---

## 1. The Module Interface (`ClientFeatureModule` & `AddonModule`)

Every client feature module implements [`ClientFeatureModule`](../v26_3/src/main/java/de/germanminer/addon/client/module/ClientFeatureModule.java) (which extends [`AddonModule`](../common/src/main/java/de/germanminer/addon/module/AddonModule.java)):

```java
package de.germanminer.addon.client.module;

import de.germanminer.addon.client.GMAddonClient;
import de.germanminer.addon.module.AddonModule;
import de.germanminer.addon.module.HudWidgetDescriptor;
import de.germanminer.addon.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Optional;

public interface ClientFeatureModule extends AddonModule {
    // From AddonModule:
    String getId();                                  // Unique ID, e.g. "bank_tracker"
    String getDisplayName();                         // Sidebar button title in Settings (O)
    String getDescription();                         // Subtitle shown in Settings
    boolean isEnabled();                             // Whether module is currently enabled
    void setEnabled(boolean enabled);                // Enable/disable callback
    default Optional<HudWidgetDescriptor> getHudWidget() { return Optional.empty(); }
    default List<Setting<?>> getSettings() { return List.of(); }

    // Client Lifecycle & Rendering Hooks:
    default void onInit(GMAddonClient addon) {}
    default void onServerJoin(GMAddonClient addon) {}
    default void onServerDisconnect(GMAddonClient addon) {}
    default void onClientTick(GMAddonClient addon, Minecraft client) {}
    default void onPlayerRespawn(GMAddonClient addon) {}
    default void onChatMessage(GMAddonClient addon, String plainMessage) {}
    default void onActionBarMessage(GMAddonClient addon, String plainMessage) {}
    default void renderWorldOverlay(GMAddonClient addon, GuiGraphicsExtractor graphics, Minecraft mc, float partialTick) {}
    default void renderHudWidget(GMAddonClient addon, GuiGraphicsExtractor graphics, Minecraft mc, float partialTick, boolean editorPreview) {}
}
```

---

## 2. Available Settings Controls (`Setting.java`)

In your module's `getSettings()` method, you can return any combination of the 6 declarative `Setting` types defined in [`Setting.java`](../common/src/main/java/de/germanminer/addon/module/Setting.java). [`GMSettingsScreen`](../v26_3/src/main/java/de/germanminer/addon/client/screen/GMSettingsScreen.java) renders them automatically:

| Setting Record | UI Control Rendered in `GMSettingsScreen` | Example Usage |
| :--- | :--- | :--- |
| **`Setting.BooleanSetting`** | ON/OFF Toggle Button (`AN (Aktiv)` / `AUS`) | Enable/disable module or sub-feature |
| **`Setting.IntSliderSetting`** | Integer Slider with unit suffix (`100 km/h`, `30 s`) | Timeouts, pixel sizes, max speed |
| **`Setting.DoubleSliderSetting`** | Stepped Decimal Slider (`0.05` step, `2.70x`, `28 m`) | Widget scale, distances, calibration factor |
| **`Setting.CycleSetting`** | Multi-Option Cycle Button (`ANALOG` ↔ `DIGITAL`) | Visual style/mode selector |
| **`Setting.StringSetting`** | Editable Text Input (`EditBox`) | URLs, API keys, custom filter strings |
| **`Setting.ActionSetting`** | Clickable Action Button (`Runnable`) | Reload database, open sub-screen, clear cache |

---

## 3. Step-by-Step Tutorial: Creating a New Plugin Module

Below is a complete, end-to-end example showing how to create a new module (for example, a **"PayDay & Kontostand HUD" (`PaydayModule`)** that parses GermanMiner's PayDay chat messages and renders a draggable HUD widget).

### Step 1: Add Persisted Fields to `AddonConfig.java`
Open [`AddonConfig.java`](../common/src/main/java/de/germanminer/addon/config/AddonConfig.java) and add your config fields with sensible defaults:

```java
    // Payday / Bank HUD Module settings
    public boolean paydayEnabled = true;
    public int paydayX = 12;
    public int paydayY = 260;
    public float paydayScale = 1.0f;
```
*(GSON automatically serializes and deserializes public fields in `config/gm-addon/config.json`.)*

### Step 2: Create the Module Class in `:v26_3`
Create `v26_3/src/main/java/de/germanminer/addon/client/module/PaydayModule.java`:

```java
package de.germanminer.addon.client.module;

import de.germanminer.addon.client.GMAddonClient;
import de.germanminer.addon.config.AddonConfig;
import de.germanminer.addon.module.HudWidgetDescriptor;
import de.germanminer.addon.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Optional;

public class PaydayModule implements ClientFeatureModule {
    private final GMAddonClient addon;
    private final HudWidgetDescriptor widgetDescriptor;
    private String lastPaydayInfo = "Noch kein PayDay";

    public PaydayModule(GMAddonClient addon) {
        this.addon = addon;
        this.widgetDescriptor = new HudWidgetDescriptor() {
            @Override
            public String getWidgetId() { return "payday_hud"; }

            @Override
            public String getDisplayName() { return "PayDay-Anzeige"; }

            @Override
            public boolean isEnabled() { return addon.getConfig().paydayEnabled; }

            @Override
            public void setEnabled(boolean enabled) {
                addon.getConfig().paydayEnabled = enabled;
                addon.saveConfig();
            }

            @Override
            public int getX() { return addon.getConfig().paydayX; }

            @Override
            public int getY() { return addon.getConfig().paydayY; }

            @Override
            public void setPosition(int x, int y) {
                addon.getConfig().paydayX = x;
                addon.getConfig().paydayY = y;
            }

            @Override
            public float getScale() { return addon.getConfig().paydayScale; }

            @Override
            public void setScale(float scale) { addon.getConfig().paydayScale = scale; }

            @Override
            public int getBaseWidth() { return 140; }

            @Override
            public int getBaseHeight() { return 28; }

            @Override
            public void resetToDefault() {
                AddonConfig cfg = addon.getConfig();
                cfg.paydayX = 12;
                cfg.paydayY = 260;
                cfg.paydayScale = 1.0f;
            }
        };
    }

    @Override
    public String getId() { return "payday"; }

    @Override
    public String getDisplayName() { return "PayDay-Tracker"; }

    @Override
    public String getDescription() { return "Zeigt deinen letzten PayDay im HUD an."; }

    @Override
    public boolean isEnabled() { return addon.getConfig().paydayEnabled; }

    @Override
    public void setEnabled(boolean enabled) {
        addon.getConfig().paydayEnabled = enabled;
        addon.saveConfig();
    }

    @Override
    public Optional<HudWidgetDescriptor> getHudWidget() {
        return Optional.of(widgetDescriptor);
    }

    @Override
    public void onChatMessage(GMAddonClient addon, String plainMessage) {
        if (plainMessage.toLowerCase().contains("payday")) {
            this.lastPaydayInfo = plainMessage.trim();
        }
    }

    @Override
    public void renderHudWidget(GMAddonClient addon, GuiGraphicsExtractor graphics, Minecraft mc, float partialTick, boolean editorPreview) {
        AddonConfig cfg = addon.getConfig();
        Font font = mc.font;

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) cfg.paydayX, (float) cfg.paydayY);
        graphics.pose().scale(cfg.paydayScale, cfg.paydayScale);

        graphics.fill(0, 0, 140, 28, 0xD90F172A);
        graphics.outline(0, 0, 140, 28, 0xFF38BDF8);
        graphics.text(font, "§b§lPayDay Status", 6, 5, 0xFFF8FAFC, true);
        graphics.text(font, lastPaydayInfo, 6, 16, 0xFFCBD5E1, false);

        graphics.pose().popMatrix();
    }

    @Override
    public List<Setting<?>> getSettings() {
        AddonConfig cfg = addon.getConfig();
        return List.of(
            new Setting.BooleanSetting(
                "payday_enabled",
                "PayDay-Widget aktivieren",
                "Zeigt das PayDay-Widget auf dem Bildschirm.",
                () -> cfg.paydayEnabled,
                val -> { cfg.paydayEnabled = val; addon.saveConfig(); }
            ),
            new Setting.DoubleSliderSetting(
                "payday_scale",
                "Widget Skalierung",
                "Skalierung des PayDay-Widgets.",
                0.6, 2.0, 0.05, "x",
                () -> (double) cfg.paydayScale,
                val -> { cfg.paydayScale = val.floatValue(); addon.saveConfig(); }
            )
        );
    }
}
```

### Step 3: Register the Module in `ModuleManager.java`
Open [`ModuleManager.java`](../v26_3/src/main/java/de/germanminer/addon/client/module/ModuleManager.java#L24-L32) and add one line to `registerDefaultModules()`:

```java
    public void registerDefaultModules() {
        registerModule(new GeneralModule(addon));
        registerModule(new MinimapModule(addon));
        registerModule(new TachoModule(addon));
        registerModule(new BlitzerModule(addon));
        registerModule(new SpawnProtectionModule(addon));
        registerModule(new DamageLogModule(addon));
        registerModule(new PaydayModule(addon)); // <-- Added here!
    }
```

That's it! Your new module now appears in the Settings GUI (`O`), in the HUD Editor (`H`), and receives all client lifecycle and chat events automatically.

---

## 4. Adding Custom Keybindings (Hotkeys)

To add a new keybinding:
1. Declare a `KeyMapping` field in [`GMAddonClient.java`](../v26_3/src/main/java/de/germanminer/addon/client/GMAddonClient.java) and register it inside `registerKeyMappings()`:
   ```java
   myCustomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
       "key.gm-addon.my_custom_key",
       InputConstants.KEY_K,
       category
   ));
   ```
2. Handle clicks inside `GMAddonClient.onClientTick(Minecraft client)`.
   - **Important Anti-Spam Rule**: If your hotkey sends a server slash command (`client.player.connection.sendCommand(...)`), **never** send commands inside a raw `while (key.consumeClick())` loop without a cooldown (at least `500ms` between commands), or rapid presses / OS key-repeat will trigger a server spam kick! Follow the rate-limited queue pattern used for Tempomat in [`GMAddonClient.onClientTick`](../v26_3/src/main/java/de/germanminer/addon/client/GMAddonClient.java#L252-L306).
3. Add translation entries for `"key.gm-addon.my_custom_key"` in both:
   - [`v26_3/src/main/resources/assets/gm-addon/lang/de_de.json`](../v26_3/src/main/resources/assets/gm-addon/lang/de_de.json)
   - [`v26_3/src/main/resources/assets/gm-addon/lang/en_us.json`](../v26_3/src/main/resources/assets/gm-addon/lang/en_us.json)
