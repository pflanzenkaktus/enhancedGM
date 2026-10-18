# Changelog — GermanMiner Addon (`gm-addon`)

All notable changes to the **GermanMiner Addon** are documented in this file.

---

## [Unreleased] (dev branch)

### Added
- **Cruise Control (Tempomat) Brake Auto-Disengage (`'S'`)**:
  - Tapping the vehicle brake / reverse key (`client.options.keyDown` / 'S') instantly switches Cruise Control from `AN` to `AUS` / disabled (`tempomatActive = false`).
  - Automatically cancels all pending queued throttle-up (`vehicles tempomat-plus`), throttle-down (`vehicles tempomat-minus`), and toggle command packets to avoid lag spikes or unintended vehicle acceleration.
- **Vehicle Capability & Unsupported Vehicle Detection**:
  - Automatically checks root vehicle entity type, custom name, and equipped 3D armor stand helmet items to identify non-motorized vehicles (e.g. bicycles, BMX, skateboards, scooters, wheelchairs, boats, minecarts, horses).
  - Automatically updates the HUD to display `KEIN TEMPOMAT` / `N/A` (Digital Tacho) or `○ Kein Tempomat` (Analog Tacho) and removes cruise target notches.
  - Intercepts GermanMiner server messages (e.g. `"Dieses Fahrzeug besitzt keinen Tempomat"`, `"nicht verfügbar"`) and immediately marks Cruise Control as unsupported for the current vehicle.
  - Automatically resets status back to available when leaving the vehicle so the next entered vehicle is cleanly tested.
- **Autonomous Cruise Telemetry Verification ("wird schneller ohne Gas")**:
  - Physics & velocity monitor when Cruise Control is active and the player releases both throttle and brake (`!keyUp && !keyDown`):
    - **Autonomous Acceleration / Cruise Hold**: If the vehicle accelerates on its own or maintains target speed without throttle input, Cruise Control is verified active and supported.
    - **Friction Coast-down Detection**: If the vehicle continually loses speed through deceleration and friction for over 1.75 seconds with no throttle input, Cruise Control is detected as inactive/unsupported on this vehicle and automatically disengages.
- **Multi-Version Compatibility Bridge (`VersionCompat`)**:
  - Added reflection-free compatibility layer supporting Minecraft **26.1.2 through 26.3**.
  - Bridges screen management (`setScreen`, `getScreen`), key input polling (`isKeyDown`, `isShiftDown`), and boss bar overlay access across breaking Minecraft 26 minor updates.

### Fixed
- **Spawnschutz & Notwehr HUD Widget Overlap**:
  - Fixed an issue where `"Login-Spawnschutz"` or long status labels collided and overlapped with the right-aligned countdown time (`14.2s`).
  - Increased base width from `152px` to `180px` (`SpawnProtectionModule`) and `165px` to `175px` (`DamageLogModule`).
  - Added dynamic width expansion with a guaranteed `16px` minimum padding between label and countdown timer so text never overlaps regardless of reason length or attacker name.
- **HUD Editor Magnetic Snapping & Shift Bypass (`HudEditorScreen`)**:
  - Fixed magnetic snapping not engaging due to an SDL scancode vs GLFW keycode mismatch in Minecraft 26.3 (`InputConstants.isKeyDown`), which caused `isShiftDown()` to intermittently return `true` and bypass snapping.
  - Added proper `keyPressed` and `keyReleased` event handlers and synchronized `shiftHeld` state with `event.hasShiftDown()` across all mouse and keyboard interactions.
  - Increased magnetic snap threshold from `7px` to `12px` for responsive, intuitive alignment across all screen resolutions and GUI scale settings.
  - Fixed mouse button event codes (Left Click = button `0`, Right Click = button `1`) so right-clicking toggles widget visibility and left-clicking handles dragging and snapping.
  - Excluded disabled HUD widgets from magnetic snap calculations so hidden elements no longer generate phantom snap guidelines.
  - Clamped snap guideline coordinates to visible screen boundaries to prevent off-screen draw calls.
  - Fixed profile mod deployment script to purge obsolete `1.0.0` JARs and cleanly install `0.1.0-BETA`.

---

## [0.1.0-BETA] — 2026-10-18

### Added
- **Modular Plugin Architecture (`:common` & `:v26_3`)**:
  - Created `AddonModule`, `ClientFeatureModule`, `ModuleManager`, `HudWidgetDescriptor`, and declarative `Setting` records (`BooleanSetting`, `IntSliderSetting`, `DoubleSliderSetting`, `CycleSetting`, `StringSetting`, `ActionSetting`).
  - Added interactive **Settings Screen (`O` & Fabric Mod Menu)** (`GMSettingsScreen`) and **Drag-and-Drop HUD Layout Editor (`H`)** (`HudEditorScreen`) with **magnetic snapping & visual alignment guide lines** (Screen Center X/Y, `10px` safe-zone margins, widget-to-widget edge/center alignment, `6px` adjacent docking, and `Shift`-key bypass).
- **BlueMap Minimap & Fullscreen Map (`MinimapModule`)**:
  - Live tile streaming and disk/GPU caching from `http://map.germanminer.de:2086/` (`BlueMapTileClient`, `BlueMapTextureCache`) with automatic `501×1002` dual-layer PNG cropping to `500×500`.
  - Live POI markers (`Tankstellen`, `Taxipunkte`, `Haltestellen`) via `BlueMapMarkerClient`.
  - Interactive **Fullscreen Map (`M`)** (`FullscreenMapScreen`) with pan, zoom, search, layer toggles, and right-click Blitzer management.
- **Speed Camera ("Blitzer") Warning System & Firebase Cloud Sync (`BlitzerModule`)**:
  - Multi-stage proximity & heading alerts (`APPROACHING`, `URGENT`, `OVER_SPEED_LIMIT`), acoustic warning chimes, and projected 3D world markers.
  - **Automatic Chat Flash Detection (`autoAdded: true`)**: Automatically detects when the player is flashed in chat (`"Du wurdest mit XX km/h in einer XX km/h-Zone geblitzt..."`), records coordinates, and uploads to Firebase Realtime Database (`gm-addon-a5507`, `europe-west1`).
  - **Non-Stacking Spatial Deduplication (`28m`)**: Automatically merges cameras within `28` blocks so intersections never stack duplicate cameras.
  - **Vehicle-Only Mode (`blitzerOnlyInVehicle`)**: Suppresses Blitzer alerts and 3D markers when walking on foot.
  - **Hardened Firebase Security Rules (`database.rules.json`)**: Enforces append-only writes (`!data.exists()`), blocks URLs/links/domains in `name` and `addedBy` via regex, validates coordinate/speed ranges, and rejects unknown fields (`$other: false`).
- **Speedometer ("Tacho") HUD & Tempomat Cruise-Control (`TachoModule`)**:
  - **2 Switchable Tacho HUD Styles**: **`ANALOG`** (circular gauge with `250°` tick marks, rotating needle, outer-rim Tempomat marker, and digital center `km/h`) and **`DIGITAL`** (modern dashboard card with `2.0x` large digits, Tempomat status box, speed limit badge, and LED progress bar).
  - **Calibrated GermanMiner Speed (`speedCalibrationFactor = 2.70`)**: Fast 20 Hz (`50ms`) root-vehicle velocity tracking calibrated to GermanMiner's exact server `km/h` (`50 km/h` server = `50 km/h` HUD).
  - **Tempomat Hotkeys & Anti-Spam Queue**: Added `G` (`/vehicles tempomat`), `UP Arrow` (`/vehicles tempomat-plus`), and `DOWN Arrow` (`/vehicles tempomat-minus`) with edge detection and a `500ms` rate-limited command dispatcher to prevent server spam kicks.
  - **3-Layer Server Vehicle Menu Sync**: Keeps the Tacho's Tempomat display synced with GermanMiner's `/vehicles` chest GUI via container slot click hooks, item lore/BossBar inspection, and steady-cruise self-healing auto-sync.
- **Damage-Log & 30-Second Notwehr System (`DamageLogModule`)**:
  - Automatic `/damagelog` chat parser (`DamageLogTracker`) evaluating every hit against the 30s self-defense window (`VALID_NOTWEHR`, `INCOMING_ATTACK`, `EXPIRED_NOTWEHR`, `FIRST_STRIKE_BY_SELF`).
  - Live **30s Notwehr HUD Countdown Widget** and interactive **Damage-Log Reviewer Screen (`L`)** (`DamageLogScreen`).
- **20-Second Spawn / Respawn Protection Timer (`SpawnProtectionModule`)**:
  - Automatic 20s Spawnschutz HUD countdown on join and respawn (`SpawnProtectionTracker`).
- **Second-Monitor Browser Web Map Bridge (`LocalWebMapBridge`)**:
  - Embedded localhost HTTP server (`http://127.0.0.1:2087/`) serving a live-synced Leaflet map.
- **Developer & AI Agent Documentation**:
  - Added `README.md`, `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/PLUGIN_AND_MODULE_GUIDE.md`, and `CHANGELOG.md`.
