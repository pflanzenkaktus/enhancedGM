# AI Agent & LLM Developer Guide (`AGENTS.md`)

> **IMPORTANT FOR ALL LLMs / AI CODING AGENTS**: Read this file before modifying any code in `e:\GM-Addon`. It documents the multi-module architecture, **Minecraft 26.3 (Unobfuscated) API changes**, build/deployment commands, and extension patterns.

---

## 1. Environment & Target Platform

- **Minecraft Version**: `26.3` (Mojang's 2026 unobfuscated release)
- **Java Version**: **Java 25** (`JavaLanguageVersion.of(25)`)
- **Fabric Loader**: `0.20.0`
- **Fabric Loom**: `1.18.4` (`net.fabricmc.fabric-loom` — unobfuscated plugin, **no `mappings` dependency** in `build.gradle`!)
- **Fabric API**: `0.163.0+26.3`
- **Active User Test Profile**: `C:\Users\PflanzenK\AppData\Roaming\ModrinthApp\profiles\Fabric 26.3`
- **Runtime Config Directory**: `<profile>/config/gm-addon/` (`config.json`, `speed_cameras.json`, `tile_cache/`)

---

## 2. Critical Minecraft 26.3 API Rules (Avoid Obsolete 1.20/1.21 Patterns!)

Minecraft `26.3` uses Mojang's official unobfuscated classes with several modernized rendering and GUI APIs. **Do NOT use old 1.20/1.21 method names**:

| Task | Obsolete (1.20 / 1.21) | **Correct in Minecraft 26.3 (`:v26_3`)** |
| :--- | :--- | :--- |
| **2D GUI Graphics Class** | `GuiGraphics` | **`net.minecraft.client.gui.GuiGraphicsExtractor`** |
| **Screen Render Method** | `render(GuiGraphics, int, int, float)` | **`extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)`** |
| **Draw Text** | `graphics.drawString(font, text, x, y, color, shadow)` | **`graphics.text(font, text, x, y, color, shadow)`** |
| **Draw Centered Text** | `graphics.drawCenteredString(font, text, x, y, color)` | **`graphics.centeredText(font, text, x, y, color)`** |
| **Draw Box Outline** | `graphics.renderOutline(x, y, w, h, color)` | **`graphics.outline(x, y, w, h, color)`** |
| **2D Pose Stack (`Matrix3x2fStack`)** | `pose().pushPose()` / `popPose()` | **`graphics.pose().pushMatrix()`** / **`graphics.pose().popMatrix()`** |
| **2D Rotation** | `pose().mulPose(Axis.ZP.rotationDegrees(deg))` | **`graphics.pose().rotate((float) Math.toRadians(deg))`** |
| **Draw Dynamic Texture** | `graphics.blit(id, x, y, u, v, w, h, tw, th)` | **`graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, tw, th)`** |
| **Open / Set Screen** | `mc.setScreen(screen)` | **`mc.gui.setScreen(screen)`** |
| **Current Open Screen** | `mc.screen` | **`mc.gui.screen()`** |
| **Screen Mouse Click** | `mouseClicked(double, double, int)` | **`mouseClicked(MouseButtonEvent event, boolean doubleClick)`** (`event.x()`, `event.y()`, `event.button()`) |
| **Screen Mouse Drag** | `mouseDragged(double, double, int, double, double)` | **`mouseDragged(MouseButtonEvent event, double dragX, double dragY)`** |
| **Screen Mouse Release** | `mouseReleased(double, double, int)` | **`mouseReleased(MouseButtonEvent event)`** |
| **Screen Key Press** | `keyPressed(int, int, int)` | **`keyPressed(KeyEvent event)`** (`event.key()`, `event.scancode()`, `event.modifiers()`) |
| **Send Chat to Local Player** | `mc.player.displayClientMessage(comp, false)` | **`mc.player.sendSystemMessage(Component.literal(...))`** |
| **Send Slash Command** | `mc.player.chat("/cmd")` | **`mc.player.connection.sendCommand("vehicles tempomat")`** (no leading `/`) |
| **Register KeyMapping** | `KeyBindingHelper.registerKeyBinding(...)` | **`KeyMappingHelper.registerKeyMapping(...)`** with **`KeyMapping.Category.register(Identifier)`** |
| **Register HUD Layer** | `HudRenderCallback.EVENT.register(...)` | **`HudElementRegistry.addLast(Identifier, (graphics, deltaTracker) -> ...)`** |

---

## 3. Architecture Separation (`:common` vs `:v26_3`)

To keep business logic testable without starting a Minecraft client, the project is split into two Gradle subprojects:

1. **`:common` (`e:\GM-Addon\common`)**:
   - **Zero Minecraft/Fabric dependencies.** Pure Java 25 + `com.google.code.gson:gson`.
   - Contains:
     - [`AddonConfig`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/config/AddonConfig.java): All persisted configuration fields and defaults.
     - [`Setting`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/module/Setting.java): Declarative UI setting records (`BooleanSetting`, `IntSliderSetting`, `DoubleSliderSetting`, `StringSetting`, `CycleSetting`, `ActionSetting`).
     - [`HudWidgetDescriptor`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/module/HudWidgetDescriptor.java): Position (`x`, `y`), `scale`, dimensions (`baseWidth`, `baseHeight`), and reset behavior for any draggable HUD widget.
     - [`SpeedCameraRepository`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/data/SpeedCameraRepository.java): Local JSON + Firebase Realtime DB / Firestore REST sync (`GET` + `PATCH`) and non-stacking spatial deduplication (`28m`).
     - [`BlitzerWarningTracker`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/feature/BlitzerWarningTracker.java): Velocity calculation (`speedCalibrationFactor = 2.70`), proximity/heading alerts, chat flash parser, and Tempomat state machine.
     - [`DamageLogTracker`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/feature/DamageLogTracker.java): `/damagelog` parser and 30-second Notwehr evaluator.
     - [`SpawnProtectionTracker`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/feature/SpawnProtectionTracker.java): 20-second Spawnschutz countdown.
     - [`BlueMapTileClient`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/map/BlueMapTileClient.java), [`BlueMapMarkerClient`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/map/BlueMapMarkerClient.java), [`LocalWebMapBridge`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/map/LocalWebMapBridge.java).
2. **`:v26_3` (`e:\GM-Addon\v26_3`)**:
   - Depends on `:common` and renders everything inside Minecraft 26.3.
   - Contains [`GMAddonClient`](file:///e:/GM-Addon/v26_3/src/main/java/de/germanminer/addon/client/GMAddonClient.java), [`ModuleManager`](file:///e:/GM-Addon/v26_3/src/main/java/de/germanminer/addon/client/module/ModuleManager.java), [`ClientFeatureModule`](file:///e:/GM-Addon/v26_3/src/main/java/de/germanminer/addon/client/module/ClientFeatureModule.java) implementations, [`HudOverlayRenderer`](file:///e:/GM-Addon/v26_3/src/main/java/de/germanminer/addon/client/render/HudOverlayRenderer.java), and interactive screens.

---

## 4. How to Add a New Feature / Module (Quick Checklist for LLMs)

Full step-by-step instructions and code templates are in **[docs/PLUGIN_AND_MODULE_GUIDE.md](file:///e:/GM-Addon/docs/PLUGIN_AND_MODULE_GUIDE.md)**. In short:

1. **Add Config Fields**: Add your persisted fields (`boolean myFeatureEnabled = true`, `int myWidgetX = 100`, etc.) to [`AddonConfig.java`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/config/AddonConfig.java).
2. **(Optional) Add Core Tracker in `:common`**: Put pure logic/parsing in `common/src/main/java/de/germanminer/addon/feature/` and add a unit test in [`CoreSystemsTest.java`](file:///e:/GM-Addon/common/src/test/java/de/germanminer/addon/CoreSystemsTest.java).
3. **Create Module in `:v26_3`**: Create `MyFeatureModule implements ClientFeatureModule` in `v26_3/src/main/java/de/germanminer/addon/client/module/`.
   - Return `List<Setting<?>>` in `getSettings()` — [`GMSettingsScreen`](file:///e:/GM-Addon/v26_3/src/main/java/de/germanminer/addon/client/screen/GMSettingsScreen.java) automatically builds a sidebar tab and controls for your module!
   - Return `Optional<HudWidgetDescriptor>` in `getHudWidget()` — [`HudEditorScreen`](file:///e:/GM-Addon/v26_3/src/main/java/de/germanminer/addon/client/screen/HudEditorScreen.java) automatically makes your HUD widget draggable, scalable with the scroll wheel, and toggleable!
4. **Register Module**: Add `registerModule(new MyFeatureModule(addon));` inside [`ModuleManager.registerDefaultModules()`](file:///e:/GM-Addon/v26_3/src/main/java/de/germanminer/addon/client/module/ModuleManager.java#L24-L31).
5. **Build, Test & Copy JAR**:
   ```powershell
   .\gradlew.bat test build; Remove-Item "C:\Users\PflanzenK\AppData\Roaming\ModrinthApp\profiles\Fabric 26.3\mods\gm-addon-*.jar" -Force -ErrorAction SilentlyContinue; Copy-Item "e:\GM-Addon\v26_3\build\libs\gm-addon-0.1.0-BETA+26.3.jar" "C:\Users\PflanzenK\AppData\Roaming\ModrinthApp\profiles\Fabric 26.3\mods\gm-addon-0.1.0-BETA+26.3.jar" -Force
   ```

---

## 5. Development Workflow & Releases (Rule for LLMs)

- **Always maintain `CHANGELOG.md`**: Whenever making code changes, feature additions, or bug fixes, record them with clear descriptions under `## [Unreleased]` in [`CHANGELOG.md`](CHANGELOG.md).
- **Always provide a detailed description/changelog in your user responses**: Clearly summarize every modified file, rationale, and functionality change.
- **Keep changes bundled on the `dev` branch**: Only merge to `main` and release on GitHub when the user explicitly requests it.
