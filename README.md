# GermanMiner Addon (`gm-addon`)

A modular, client-side **Minecraft 26.3 Fabric Addon** engineered specifically for **GermanMinerDE (`germanminer.de`)**.

It provides a live **BlueMap Minimap & Fullscreen Map**, a **Cloud-Synced Speed Camera ("Blitzer") Warning & Auto-Detection System** backed by **Firebase Realtime Database**, an **Analog & Digital Speedometer ("Tacho") with Tempomat Cruise-Control Hotkeys**, a **30-Second Notwehr (Self-Defense) & `/damagelog` Analyzer**, a **20-Second Spawnschutz Timer**, a **Second-Monitor Browser Map Bridge**, and a **Drag-and-Drop HUD Editor**.

---

## Documentation Index

| Document | Audience | Contents |
| :--- | :--- | :--- |
| **[README.md](README.md)** | Users | Project overview, features, keybindings, build commands, and file layout. |
| **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** | Developers | Deep-dive technical documentation on how every subsystem works internally (BlueMap tiles, Firebase sync, Blitzer auto-detection, Tacho/Tempomat sync, Damage-Log parser, HUD rendering). |
| **[docs/PLUGIN_AND_MODULE_GUIDE.md](docs/PLUGIN_AND_MODULE_GUIDE.md)** | Developers | Step-by-step developer guide with copy-pasteable templates for creating new feature modules ("plugins"), HUD widgets, Settings controls, and keybindings. |

---

## Feature Overview

1. **BlueMap Minimap & Interactive Fullscreen Map (`MinimapModule`)**:
   - Streams and caches live map tiles (`500×500` block low-res tiles cropped from BlueMap's `501×1002` dual-layer PNGs) and POI markers (`Tankstellen`, `Taxipunkte`, `Haltestellen`) directly from `http://map.germanminer.de:2086/`.
   - **Rotating or North-Locked Minimap** with live coordinates, speed (`km/h`), cardinal compass, POI markers, and flashing Speed Camera icons.
   - **Fullscreen Map (`M`)** with pan/zoom, layer toggles, search, and **Right-Click Blitzer Creation / Shift+Right-Click Deletion**.

2. **Speed Camera ("Blitzer") Warning & Firebase Cloud Database (`BlitzerModule`)**:
   - **Multi-Stage Proximity & Heading Alerts**: Evaluates 3D distance, movement/look heading cone, and current `km/h` vs. camera speed limit (`APPROACHING`, `URGENT`, `OVER_SPEED_LIMIT`), plus 3D floating world markers and acoustic warning chimes.
   - **Automatic Chat Flash Detection (`autoAdded: true`)**: Detects when GermanMiner flashes you in chat (`"Du wurdest mit 77 km/h in einer 70 km/h-Zone geblitzt..."`), extracts the speed limit, records your exact coordinates with `"autoAdded": true`, and uploads (`PATCH`) the new camera to Firebase (`https://gm-addon-a5507-default-rtdb.europe-west1.firebasedatabase.app/speed_cameras.json`).
   - **Non-Stacking Spatial Deduplication (`28m` radius)**: Automatically merges nearby cameras within `blitzerDedupeRadiusBlocks` so multiple flashes at the same intersection never stack duplicate icons.
   - **Vehicle-Only Mode (`blitzerOnlyInVehicle`)**: Suppresses warnings, sounds, and 3D world markers when walking on foot.

3. **Analog & Digital Speedometer ("Tacho") & Tempomat (`TachoModule`)**:
   - **Calibrated GermanMiner Speed (`speedCalibrationFactor = 2.70`)**: Converts raw entity velocity (`blocks/s`) at 20 TPS (`50ms` response) into GermanMiner's exact server `km/h` (`18.52 blocks/s × 2.70 = 50.0 km/h`).
   - **2 Switchable HUD Styles**:
     - **`ANALOG`**: Circular instrument cluster with `250°` tick arc, rotating needle, outer-rim Tempomat marker, center digital `km/h`, and bottom Tempomat badge.
     - **`DIGITAL`**: Modern dashboard card with `2.0x` large digits, Tempomat status box, active Blitzer speed-limit badge, and LED progress bar with Tempomat target notch.
   - **Anti-Spam Tempomat Hotkeys & 3-Layer Server Menu Sync**:
     - Rate-limited command dispatcher (`1 command / 500ms`) for `/vehicles tempomat` (`G`), `/vehicles tempomat-plus` (`UP`), and `/vehicles tempomat-minus` (`DOWN`) so rapid key presses never trigger server spam kicks.
     - Stays in sync with GermanMiner's server-provided vehicle GUI menu via container slot click hooks, item lore/BossBar inspection, and steady-cruise self-healing auto-sync.

4. **Damage-Log & 30-Second Notwehr System (`DamageLogModule`)**:
   - Automatically parses GermanMiner's `/damagelog` chat output (`HH:mm:ss ● Attacker » Victim DMG`) and evaluates every combat hit against the 30-second self-defense rule (`VALID_NOTWEHR`, `INCOMING_ATTACK`, `EXPIRED_NOTWEHR`, `FIRST_STRIKE_BY_SELF`).
   - Includes a live **30s Notwehr HUD Countdown Widget** when attacked and an interactive **Damage-Log Reviewer Screen (`L`)**.

5. **20-Second Spawn / Respawn Protection Timer (`SpawnProtectionModule`)**:
   - Displays a 20s countdown bar on server join or player respawn, and cancels early if the server announces spawn protection expiration in chat.

6. **Second-Monitor Browser Web Map Bridge (`LocalWebMapBridge`)**:
   - Starts an embedded HTTP server on `http://127.0.0.1:2087/` that serves an interactive Leaflet map synced at 5 Hz with your live in-game position, heading, speed, and Blitzer alerts.

---

## Default Keybindings

All keybindings are registered under the **"GermanMiner Addon"** category in Minecraft's standard *Options → Controls → Key Binds* menu:

| Key | Translation Key | Action |
| :--- | :--- | :--- |
| **`M`** | `key.gm-addon.open_map` | Open Interactive Fullscreen BlueMap (`FullscreenMapScreen`) |
| **`H`** | `key.gm-addon.open_hud_editor` | Open Drag-and-Drop HUD Layout Editor (`HudEditorScreen`) |
| **`O`** | `key.gm-addon.open_settings` | Open Modular Addon Settings Screen (`GMSettingsScreen`) |
| **`L`** | `key.gm-addon.open_damagelog` | Open Damage-Log & 30s Notwehr Reviewer (`DamageLogScreen`) |
| **`G`** | `key.gm-addon.tempomat_toggle` | Toggle Vehicle Tempomat ON/OFF (`/vehicles tempomat`) |
| **`UP Arrow`** | `key.gm-addon.tempomat_plus` | Vehicle Tempomat `+10 km/h` (`/vehicles tempomat-plus`) |
| **`DOWN Arrow`** | `key.gm-addon.tempomat_minus` | Vehicle Tempomat `-10 km/h` (`/vehicles tempomat-minus`) |
| **`=` (`+`)** | `key.gm-addon.zoom_in` | Zoom In Minimap |
| **`-`** | `key.gm-addon.zoom_out` | Zoom Out Minimap |

---

## Project Structure

```text
gm-addon/
├── build.gradle                 # Root Gradle build (Java 25 toolchain, JUnit 5)
├── settings.gradle              # Multi-project setup (:common, :v26_3)
├── gradle.properties            # Minecraft 26.3, Fabric Loader 0.20.0, Fabric API 0.163.0+26.3
├── firebase.json                # Firebase CLI config for Realtime Database rules
├── database.rules.json          # Firebase Realtime Database security & validation rules
├── common/                      # Version-independent core logic, models, networking & unit tests
│   └── src/
│       ├── main/java/de/germanminer/addon/
│       │   ├── config/          # AddonConfig.java (GSON persistence & defaults)
│       │   ├── data/            # SpeedCameraRepository.java (Local JSON + Firebase REST sync & deduplication)
│       │   ├── feature/         # BlitzerWarningTracker, DamageLogTracker, SpawnProtectionTracker
│       │   ├── map/             # BlueMapTileClient, BlueMapMarkerClient, LocalWebMapBridge
│       │   ├── model/           # SpeedCamera, BlueMapMarker
│       │   └── module/          # AddonModule, HudWidgetDescriptor, Setting (sealed interface)
│       └── test/java/de/germanminer/addon/
│           └── CoreSystemsTest.java # JUnit 5 test suite for tiles, Firebase, Blitzer, Tempomat, DamageLog
└── v26_3/                       # Minecraft 26.3 Fabric client implementation & UI rendering
    └── src/main/
        ├── java/de/germanminer/addon/client/
        │   ├── GMAddonClient.java           # ClientModInitializer entrypoint, keybinds & tick dispatcher
        │   ├── compat/GMModMenuIntegration.java # Fabric Mod Menu config screen hook
        │   ├── map/BlueMapTextureCache.java # GPU DynamicTexture upload & caching for BlueMap tiles
        │   ├── module/                      # ClientFeatureModule + ModuleManager + 6 feature modules
        │   ├── render/HudOverlayRenderer.java # All 2D HUD widgets & 3D world projection rendering
        │   └── screen/                      # GMSettingsScreen, HudEditorScreen, FullscreenMapScreen, DamageLogScreen
        └── resources/
            ├── fabric.mod.json
            └── assets/gm-addon/lang/        # de_de.json, en_us.json
```

---

## Build & Test Commands

### 1. Run Unit Tests & Build JAR
```bash
./gradlew test build
```
The compiled mod JAR will be located at `v26_3/build/libs/gm-addon-0.1.0-BETA+26.3.jar`.

### 2. Install to Minecraft Fabric Profile
Copy the generated JAR from `v26_3/build/libs/` into your Minecraft profile's `mods/` directory.

### 3. Deploy Firebase Realtime Database Security Rules (Admin)
```bash
firebase deploy --only database
```
