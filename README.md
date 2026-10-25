# GermanMiner Addon (`enhancedGM`)

Modulares Client-Addon für **GermanMiner** auf Basis von Fabric (Minecraft 26.1.2 – 26.3).

> [!IMPORTANT]
> **Haftungsausschluss & Unabhängigkeit / Disclaimer & Privacy Limitation**:
> 1. **Unabhängigkeit**: enhancedGM ist ein rein privates Community-Projekt und steht in **keiner offiziellen Verbindung** zur Serverleitung oder Administration von GermanMiner.de. / *enhancedGM is an independent community project and is not affiliated with or endorsed by GermanMiner.de.*
> 2. **Gewährleistungsausschluss**: Die Software und alle Netzwerkdienste (einschließlich Cloud-Sync) werden ohne jede Gewähr oder Garantie („AS IS“) bereitgestellt. Die Autoren übernehmen keinerlei Haftung für Richtigkeit, Fehler, Datenverlust, In-Game-Bußgelder oder etwaige datenschutz- bzw. sicherheitsrelevante Risiken und Fehlkonfigurationen. Die Nutzung erfolgt vollständig freiwillig und auf eigenes Risiko. / *This software is provided "AS IS", without warranties of any kind. The authors disclaim all liability for software defects, in-game penalties, data loss, or privacy/security flaws. Use at your own risk.*

* **BlueMap Minimap & Vollbildkarte (`M`)**: Live-Kartenkacheln, POI-Marker (Tanken, Taxis, Haltestellen) und Zoom.
* **Blitzer-Warnsystem & Cloud-Sync**: 3D-Bojen, Annäherungswarnung, automatische Chat-Erkennung und Firebase-Sync.
* **Tacho & Tempomat**: Analog- und Digital-Tacho, Auto-Abschaltung beim Bremsen (`S`) und Fahrzeugerkennung.
* **20s Spawnschutz-Timer**: Automatischer Countdown bei Server-Join und Respawn.
* **Grundstück-Erinnerung (60-Tage-Regel)**: Scan von `/menu` → Grundstücke & Wohnungen, Besuchserkennung über die Actionbar (Spieler oder Firma), Übersicht mit Frist und Join-Erinnerung.
* **Magnetischer HUD-Editor (`H`)**: Frei verschiebbare HUD-Widgets mit Einrastfunktion (`Shift` = frei bewegen).

---

## Documentation Index

| Document | Audience | Contents |
| :--- | :--- | :--- |
| **[README.md](README.md)** | Users | Project overview, features, keybindings, build commands, and file layout. |
| **[CHANGELOG.md](CHANGELOG.md)** | Users & Developers | Full version history and release notes across all releases. |
| **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** | Developers | Deep-dive technical documentation on how every subsystem works internally (BlueMap tiles, Firebase sync, Blitzer auto-detection, Tacho/Tempomat sync, HUD rendering). |
| **[docs/PLUGIN_AND_MODULE_GUIDE.md](docs/PLUGIN_AND_MODULE_GUIDE.md)** | Developers | Step-by-step developer guide with copy-pasteable templates for creating new feature modules ("plugins"), HUD widgets, Settings controls, and keybindings. |
| **[docs/PRIVACY_AND_SECURITY.md](docs/PRIVACY_AND_SECURITY.md)** | Users & Developers | Technical privacy overview, transmitted data fields, local storage paths, and network security policies. |

---

## Feature Overview

1. **BlueMap Minimap & Interactive Fullscreen Map (`MinimapModule`)**:
   - Streams and caches live map tiles (`500×500` block low-res tiles) and POI markers (`Tankstellen`, `Taxipunkte`, `Haltestellen`) directly from `http://map.germanminer.de:2086/`.
   - **Rotating or North-Locked Minimap** with live coordinates, speed (`km/h`), cardinal compass, POI markers, and flashing Speed Camera icons.
   - **Fullscreen Map (`M`)** with smooth drag-to-pan, scroll-to-zoom (`LOD 1` & `LOD 2`), follow-player tracking, marker category toggles (speed cameras, gas stations, taxi points, transit stops), and hover tooltips.

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
     - **Brake Auto-Disengage (`S`)**: Tapping the vehicle brake/reverse key instantly cancels Cruise Control without manual menu navigation.
     - **Automatischer Motorstart (Auto Engine Start)**: Automatically starts vehicle engines via `/vehicles motor` upon entering when the BossBar indicates the engine is turned off (non-motorized vehicles like bikes, skateboards, and wheelchairs are smartly ignored).

4. **20-Second Spawn / Respawn Protection Timer (`SpawnProtectionModule`)**:
   - Displays a 20s countdown bar on server join or player respawn, and cancels early if the server announces spawn protection expiration in chat.

5. **Grundstücks- & Wohnungs-Erinnerungs-System (`PlotModule`)**:
   - **Enteignungs-Schutz (60-Tage-Regel)**: Verhindert das automatische Enteignen von Grundstücken und Wohnungen auf GermanMiner.
   - **Automatischer GUI-Scanner**: Liest beim Öffnen von `/menu` -> Grundstücke & Wohnungen alle Besitztümer über mehrere Seiten und Ebenen hinweg vollautomatisch ein.
   - **Live-Tracking & Chat-/ActionBar-Erkennung**: Aktualisiert die Frist automatisch beim Betreten eigener Grundstücke.
   - **Vollbild-Übersicht (`PlotOverviewScreen`)**: Sortierbare Tabelle aller Grundstücke mit verbleibenden Tagen, Fristen und Farbwarnung.
   - **HUD-Widget**: Zeigt das Grundstück mit der kürzesten verbleibenden Frist permanent oder bei Annäherung an die Warnschwelle an.

6. **Magnetic HUD Layout Editor (`HudEditorScreen`) & Modular Settings (`GMSettingsScreen`)**:
   - **In-Game HUD Editor (`H`)**: Freely reposition all HUD widgets with smart magnetic snapping to screen edges, screen center axes, and neighboring widgets (hold `Shift` for free movement). Right-click any widget to toggle it ON/OFF, and use the scroll wheel to resize scale from 50% to 250% in clean 5% increments.
   - **Modular Settings (`O`) & Mod Menu**: In-game configuration screen featuring categorical tabs, interactive sliders, toggle switches, and a dedicated **Debug & Erweitert** menu for simulation and developer actions.
   - **Privacy-First Community Cloud Sync**: Opt-in prompt on first startup (`CommunitySyncPromptScreen`) giving you complete control over whether speed camera positions sync with Firebase.
   - **Full Bilingual Localization**: Complete German (`de_de`) and English (`en_us`) language support with automatic client detection or in-game override.

---

## Default Keybindings

All keybindings are registered under the **"GermanMiner Addon"** category in Minecraft's standard *Options → Controls → Key Binds* menu:

| Key | Translation Key | Action |
| :--- | :--- | :--- |
| **`M`** | `key.gm-addon.open_map` | Open Interactive Fullscreen BlueMap (`FullscreenMapScreen`) |
| **`H`** | `key.gm-addon.open_hud_editor` | Open Drag-and-Drop HUD Layout Editor (`HudEditorScreen`) |
| **`O`** | `key.gm-addon.open_settings` | Open Modular Addon Settings Screen (`GMSettingsScreen`) |
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
├── common/                      # Version-independent core logic, models, networking & unit tests
│   └── src/
│       ├── main/java/de/pflanzenkaktus/enhancedgm/
│       │   ├── config/          # AddonConfig.java (GSON persistence & defaults)
│       │   ├── data/            # SpeedCameraRepository.java, PlotRepository.java
│       │   ├── feature/         # BlitzerWarningTracker, SpawnProtectionTracker, PlotTracker
│       │   ├── map/             # BlueMapTileClient, BlueMapMarkerClient, LocalWebMapBridge
│       │   ├── model/           # SpeedCamera, BlueMapMarker, OwnedPlot
│       │   └── module/          # AddonModule, HudWidgetDescriptor, Setting (sealed interface)
│       └── test/java/de/pflanzenkaktus/enhancedgm/
│           └── CoreSystemsTest.java, PlotTrackerTest.java # JUnit 5 test suites
└── v26_3/                       # Minecraft 26.3 Fabric client implementation & UI rendering
    └── src/main/
        ├── java/de/pflanzenkaktus/enhancedgm/client/
        │   ├── GMAddonClient.java           # ClientModInitializer entrypoint, keybinds & tick dispatcher
        │   ├── compat/GMModMenuIntegration.java, VersionCompat.java # Cross-version runtime hooks
        │   ├── map/BlueMapTextureCache.java # GPU DynamicTexture upload & caching for BlueMap tiles
        │   ├── module/                      # ClientFeatureModule + ModuleManager + 6 feature modules
        │   ├── render/HudOverlayRenderer.java # All 2D HUD widgets & 3D world projection rendering
        │   └── screen/                      # GMSettingsScreen, HudEditorScreen, FullscreenMapScreen, PlotOverviewScreen, PlotScanPromptScreen
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
The compiled mod JAR will be located at `v26_3/build/libs/enhancedGM-0.3.0.jar`.

### 2. Install to Minecraft Fabric Profile
Copy the generated JAR from `v26_3/build/libs/` into your Minecraft profile's `mods/` directory.

---

## License

All Rights Reserved. Copyright (c) 2026 pflanzenkaktus. See [LICENSE](LICENSE) for details.
