# Changelog — GermanMiner Addon (`gm-addon`)

All notable changes to the **GermanMiner Addon** are documented in this file.

---

## [0.1.0-BETA] — 2026-10-18

### Added
- **Modular Plugin Architecture (`:common` & `:v26_3`)**:
  - Created [`AddonModule`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/module/AddonModule.java), [`ClientFeatureModule`](file:///e:/GM-Addon/v26_3/src/main/java/de/germanminer/addon/client/module/ClientFeatureModule.java), [`ModuleManager`](file:///e:/GM-Addon/v26_3/src/main/java/de/germanminer/addon/client/module/ModuleManager.java), [`HudWidgetDescriptor`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/module/HudWidgetDescriptor.java), and declarative [`Setting`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/module/Setting.java) records (`BooleanSetting`, `IntSliderSetting`, `DoubleSliderSetting`, `CycleSetting`, `StringSetting`, `ActionSetting`).
  - Added interactive **Settings Screen (`O` & Fabric Mod Menu)** ([`GMSettingsScreen`](file:///e:/GM-Addon/v26_3/src/main/java/de/germanminer/addon/client/screen/GMSettingsScreen.java)) and **Drag-and-Drop HUD Layout Editor (`H`)** ([`HudEditorScreen`](file:///e:/GM-Addon/v26_3/src/main/java/de/germanminer/addon/client/screen/HudEditorScreen.java)) with **magnetic snapping & visual alignment guide lines** (Screen Center X/Y, `10px` safe-zone margins, widget-to-widget edge/center alignment, `6px` adjacent docking, and `Shift`-key bypass).
- **BlueMap Minimap & Fullscreen Map (`MinimapModule`)**:
  - Live tile streaming and disk/GPU caching from `http://map.germanminer.de:2086/` ([`BlueMapTileClient`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/map/BlueMapTileClient.java), [`BlueMapTextureCache`](file:///e:/GM-Addon/v26_3/src/main/java/de/germanminer/addon/client/map/BlueMapTextureCache.java)) with automatic `501×1002` dual-layer PNG cropping to `500×500`.
  - Live POI markers (`Tankstellen`, `Taxipunkte`, `Haltestellen`) via [`BlueMapMarkerClient`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/map/BlueMapMarkerClient.java).
  - Interactive **Fullscreen Map (`M`)** ([`FullscreenMapScreen`](file:///e:/GM-Addon/v26_3/src/main/java/de/germanminer/addon/client/screen/FullscreenMapScreen.java)) with pan, zoom, search, layer toggles, and right-click Blitzer management.
- **Speed Camera ("Blitzer") Warning System & Firebase Cloud Sync (`BlitzerModule`)**:
  - Multi-stage proximity & heading alerts (`APPROACHING`, `URGENT`, `OVER_SPEED_LIMIT`), acoustic warning chimes, and projected 3D world markers.
  - **Automatic Chat Flash Detection (`autoAdded: true`)**: Automatically detects when the player is flashed in chat (`"Du wurdest mit XX km/h in einer XX km/h-Zone geblitzt..."`), records coordinates, and uploads to Firebase Realtime Database (`gm-addon-a5507`, `europe-west1`).
  - **Non-Stacking Spatial Deduplication (`28m`)**: Automatically merges cameras within `28` blocks so intersections never stack duplicate cameras.
  - **Vehicle-Only Mode (`blitzerOnlyInVehicle`)**: Suppresses Blitzer alerts and 3D markers when walking on foot.
  - **Hardened Firebase Security Rules ([`database.rules.json`](file:///e:/GM-Addon/database.rules.json))**: Enforces append-only writes (`!data.exists()`), blocks URLs/links/domains in `name` and `addedBy` via regex, validates coordinate/speed ranges, and rejects unknown fields (`$other: false`).
- **Speedometer ("Tacho") HUD & Tempomat Cruise-Control (`TachoModule`)**:
  - **2 Switchable Tacho HUD Styles**: **`ANALOG`** (circular gauge with `250°` tick marks, rotating needle, outer-rim Tempomat marker, and digital center `km/h`) and **`DIGITAL`** (modern dashboard card with `2.0x` large digits, Tempomat status box, speed limit badge, and LED progress bar).
  - **Calibrated GermanMiner Speed (`speedCalibrationFactor = 2.70`)**: Fast 20 Hz (`50ms`) root-vehicle velocity tracking calibrated to GermanMiner's exact server `km/h` (`50 km/h` server = `50 km/h` HUD).
  - **Tempomat Hotkeys & Anti-Spam Queue**: Added `G` (`/vehicles tempomat`), `UP Arrow` (`/vehicles tempomat-plus`), and `DOWN Arrow` (`/vehicles tempomat-minus`) with edge detection and a `500ms` rate-limited command dispatcher to prevent server spam kicks.
  - **3-Layer Server Vehicle Menu Sync**: Keeps the Tacho's Tempomat display synced with GermanMiner's `/vehicles` chest GUI via container slot click hooks, item lore/BossBar inspection, and steady-cruise self-healing auto-sync.
- **Damage-Log & 30-Second Notwehr System (`DamageLogModule`)**:
  - Automatic `/damagelog` chat parser ([`DamageLogTracker`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/feature/DamageLogTracker.java)) evaluating every hit against the 30s self-defense window (`VALID_NOTWEHR`, `INCOMING_ATTACK`, `EXPIRED_NOTWEHR`, `FIRST_STRIKE_BY_SELF`).
  - Live **30s Notwehr HUD Countdown Widget** and interactive **Damage-Log Reviewer Screen (`L`)** ([`DamageLogScreen`](file:///e:/GM-Addon/v26_3/src/main/java/de/germanminer/addon/client/screen/DamageLogScreen.java)).
- **20-Second Spawn / Respawn Protection Timer (`SpawnProtectionModule`)**:
  - Automatic 20s Spawnschutz HUD countdown on join and respawn ([`SpawnProtectionTracker`](file:///e:/GM-Addon/common/src/main/java/de/germanminer/addon/feature/SpawnProtectionTracker.java)).
- **Second-Monitor Browser Web Map Bridge (`LocalWebMapBridge`)**:
  - Embedded localhost HTTP server (`http://127.0.0.1:2087/`) serving a live-synced Leaflet map.
- **Developer & AI Agent Documentation**:
  - Added [`README.md`](file:///e:/GM-Addon/README.md), [`AGENTS.md`](file:///e:/GM-Addon/AGENTS.md), [`docs/ARCHITECTURE.md`](file:///e:/GM-Addon/docs/ARCHITECTURE.md), [`docs/PLUGIN_AND_MODULE_GUIDE.md`](file:///e:/GM-Addon/docs/PLUGIN_AND_MODULE_GUIDE.md), and [`CHANGELOG.md`](file:///e:/GM-Addon/CHANGELOG.md).
