# GermanMiner Addon (`gm-addon`) — Complete System Architecture

This document explains how every part of the **GermanMiner Addon** works internally, from startup and server-detection to BlueMap tile streaming, Firebase Realtime Database synchronization, Speed Camera ("Blitzer") auto-detection, Speedometer ("Tacho") & Tempomat synchronization.

---

## 1. High-Level Architecture & Lifecycle

The addon is divided into two Gradle subprojects so all core algorithms can be unit-tested in pure Java (`:common`) while `:v26_3` handles Minecraft 26.3 rendering, keybindings, and Fabric events.

```mermaid
flowchart TD
    subgraph v26_3["Minecraft 26.3 Client (:v26_3)"]
        Entry["GMAddonClient (ClientModInitializer)"]
        MM["ModuleManager"]
        HUD["HudOverlayRenderer"]
        TexCache["BlueMapTextureCache (GPU DynamicTexture)"]
        Screens["GMSettingsScreen (O) / HudEditorScreen (H) / FullscreenMapScreen (M) / PlotOverviewScreen / PlotScanPromptScreen"]

        subgraph Modules["Registered ClientFeatureModules"]
            M1["GeneralModule"]
            M2["MinimapModule"]
            M3["TachoModule"]
            M4["BlitzerModule"]
            M5["SpawnProtectionModule"]
            M6["PlotModule"]
        end
    end

    subgraph common["Platform-Independent Core (:common)"]
        Cfg["AddonConfig (config.json)"]
        Repo["SpeedCameraRepository (speed_cameras.json + Firebase REST)"]
        PlotRepo["PlotRepository (plots.json)"]
        Tile["BlueMapTileClient (Disk + RAM Cache)"]
        Marker["BlueMapMarkerClient (markers.json)"]
        Blitzer["BlitzerWarningTracker (Velocity + Alerts + Tempomat)"]
        Spawn["SpawnProtectionTracker (20s Timer)"]
        PlotTrk["PlotTracker (Menu & Actionbar Parser)"]
    end

    Entry --> Cfg
    Entry --> Repo
    Entry --> PlotRepo
    Entry --> Tile
    Entry --> Marker
    Entry --> Blitzer
    Entry --> Spawn
    Entry --> MM
    Entry --> HUD
    MM --> Modules
    Modules --> HUD
    HUD --> TexCache
    TexCache --> Tile
```

### Server Activation Check (`isActiveOnCurrentServer()`)
In [`GMAddonClient.isActiveOnCurrentServer()`](../v26_3/src/main/java/de/pflanzenkaktus/enhancedgm/client/GMAddonClient.java#L350-L364):
- By default, `config.forceEnableEverywhere = false` for release safety; it can be enabled in Settings for testing on other servers.
- When `forceEnableEverywhere = false`, the addon checks `Minecraft.getInstance().getCurrentServer().ip` against `config.serverDomainPattern` (`"germanminer.de"`).

---

## 2. Subsystem Deep Dives

### 2.1 BlueMap Tile Streaming & Dual-Layer PNG Cropping
- **Classes**: [`BlueMapTileClient`](../common/src/main/java/de/pflanzenkaktus/enhancedgm/map/BlueMapTileClient.java), [`BlueMapTextureCache`](../v26_3/src/main/java/de/pflanzenkaktus/enhancedgm/client/map/BlueMapTextureCache.java), [`BlueMapMarkerClient`](../common/src/main/java/de/pflanzenkaktus/enhancedgm/map/BlueMapMarkerClient.java)
- **How GermanMiner's BlueMap Works**:
  - GermanMiner hosts BlueMap at `http://map.germanminer.de:2086/` with map ID `world`.
  - Low-resolution tiles (`lod = 1`, `lod = 2`, `lod = 3`) are served at:
    ```text
    http://map.germanminer.de:2086/maps/world/tiles/{lod}/{xPath}/{zPath}.png
    ```
  - **Signed Digit-Split Coordinate Path (`formatSignedCoordPath`)**:
    - BlueMap splits multi-digit tile coordinates into nested folders: e.g., tile `x = -12` becomes `x-1/2`, tile `z = -4` becomes `z-4`, and tile `x = 35` becomes `x3/5`.
  - **Tile Math (`LOD 1`)**:
    - At `lod = 1`, each tile covers **`500 × 500` Minecraft blocks** (`500 × 2^(lod-1)`).
    - Block coordinate `b` maps to tile coordinate `Math.floorDiv((int) Math.floor(b), 500)`.
  - **Tile Caching Pipeline**:
    - Downloaded tiles are cached locally to disk (`config/gm-addon/tile_cache/`) with HTTP `If-None-Match` ETag caching and TTL management. Tiles are then uploaded to the GPU via an LRU-managed [`BlueMapTextureCache`](../v26_3/src/main/java/de/pflanzenkaktus/enhancedgm/client/map/BlueMapTextureCache.java).

---

### 2.2 Speed Camera ("Blitzer") Database, Auto-Detection & Firebase Sync
- **Classes**: [`SpeedCamera`](../common/src/main/java/de/pflanzenkaktus/enhancedgm/model/SpeedCamera.java), [`SpeedCameraRepository`](../common/src/main/java/de/pflanzenkaktus/enhancedgm/data/SpeedCameraRepository.java), [`BlitzerWarningTracker`](../common/src/main/java/de/pflanzenkaktus/enhancedgm/feature/BlitzerWarningTracker.java), [`BlitzerModule`](../v26_3/src/main/java/de/pflanzenkaktus/enhancedgm/client/module/BlitzerModule.java)
- **Data Model (`SpeedCamera`)**:
  ```json
  {
    "id": "auto_-2131_-1436",
    "name": "Blitzer 70er (-2131, -1436)",
    "x": -2131.3,
    "y": 65.0,
    "z": -1436.1,
    "speedLimitKmh": 70,
    "directionDegrees": 180.0,
    "triggerRadius": 0.0,
    "autoAdded": true,
    "addedBy": "GermanMinerPlayer",
    "createdAtEpochMs": 1792283552105
  }
  ```
- **Automatic Chat Flash Detection (`parseFlashedChatMessage`)**:
  - When a player gets flashed on GermanMiner, the server sends:
    `"Du wurdest mit 77 km/h in einer 70 km/h-Zone geblitzt. Du hast ein Bußgeld in Höhe von 10,00 Euro und 0 Punkte erhalten."`
  - [`BlitzerWarningTracker.parseFlashedChatMessage`](../common/src/main/java/de/pflanzenkaktus/enhancedgm/feature/BlitzerWarningTracker.java#L75-L124) strips formatting codes, ignores player chat (`Globalchat`, `[Zivilist]`, `Werbung`, `Megafon`), extracts the measured speed (`77`) and zone speed limit (`70`), and returns `FlashedEvent(77, 70)`.
- **Non-Stacking Spatial Deduplication (`blitzerDedupeRadiusBlocks = 28.0`)**:
  - Before adding any camera, [`SpeedCameraRepository.addOrMergeCamera`](../common/src/main/java/de/pflanzenkaktus/enhancedgm/data/SpeedCameraRepository.java) checks if any existing camera is within `28.0` blocks horizontally (and `25.0` blocks vertically).
  - If an existing camera is found within `28` blocks, **no second stacked camera is created**. Manually verified cameras (`autoAdded: false`) always take priority over auto-added entries.
- **Firebase Realtime Database Sync**:
  - Endpoint: `https://gm-addon-a5507-default-rtdb.europe-west1.firebasedatabase.app/speed_cameras.json`
  - Security Rules: Cloud database enforces append-only writes, URL blocking, and coordinate range limits.
  - On join and every 5 minutes, `syncWithFirebaseAsync()` performs an HTTP `GET`, merges remote and local cameras through the spatial deduplicator, and saves locally.
  - When a new Blitzer is auto-detected in chat, `uploadSingleCameraToFirebaseAsync()` immediately sends an HTTP `PATCH` to `/speed_cameras/{id}.json` so all other addon users receive the new camera.

---


### 2.3 Calibrated Speed (`km/h`), Analog/Digital Tacho & Tempomat Sync
- **Classes**: [`BlitzerWarningTracker`](../common/src/main/java/de/pflanzenkaktus/enhancedgm/feature/BlitzerWarningTracker.java), [`TachoModule`](../v26_3/src/main/java/de/pflanzenkaktus/enhancedgm/client/module/TachoModule.java), [`HudOverlayRenderer.renderTachoWidget`](../v26_3/src/main/java/de/pflanzenkaktus/enhancedgm/client/render/HudOverlayRenderer.java)
- **Why `speedCalibrationFactor = 2.70` is Used**:
  - In standard SI units, $1\text{ m/s} = 3.6\text{ km/h}$. However, GermanMiner's vehicle plugin scales movement speed so that a vehicle driving at **`50 km/h` server speed** moves at **$\approx 18.52\text{ blocks/s}$** ($18.52 \times 3.6 = 66.67$).
  - Multiplying `smoothedSpeedBlocksPerSec` by **`2.70`** ($18.52 \times 2.70 = 50.0\text{ km/h}$) produces the exact server `km/h`.
- **20 Hz Instant Speed Calculation**:
  - Every client tick (`50ms`), [`GMAddonClient.onClientTick`](../v26_3/src/main/java/de/pflanzenkaktus/enhancedgm/client/GMAddonClient.java) reads the root vehicle entity's position (`client.player.getRootVehicle()`) and updates `smoothedSpeedBlocksPerSec` using a fixed `0.05s` tick delta (eliminating frame-time jitter) and a fast `70%` new / `30%` previous filter (with immediate `0 km/h` snap when stopped).
- **Anti-Spam Tempomat Command Queue & 3-Layer Server Menu Sync**:
  - GermanMiner's Tempomat commands are `/vehicles tempomat`, `/vehicles tempomat-plus`, and `/vehicles tempomat-minus`.
  - Sending multiple slash commands in `< 500ms` triggers a server spam kick. Therefore, [`GMAddonClient`](../v26_3/src/main/java/de/pflanzenkaktus/enhancedgm/client/GMAddonClient.java) uses edge detection (ignoring OS key-repeat spam) and queues `+10` / `-10` steps in `pendingTempomatDeltaSteps`, dispatching at most **1 command per `500ms`** while updating the HUD immediately.
  - To prevent desync when changing Tempomat via the server's chest GUI (`/vehicles`), [`TachoModule`](../v26_3/src/main/java/de/pflanzenkaktus/enhancedgm/client/module/TachoModule.java) uses 3 layers:
    1. **Container Slot Click Hook (`onContainerScreenClick`)**: Uses `ScreenMouseEvents.beforeMouseClick` to detect clicks on Tempomat / `+10` / `-10` items inside `AbstractContainerScreen`.
    2. **Item Lore / BossBar / Chat Inspection**: Scans open container slots, hotbar items, BossBars, and chat messages for Tempomat status and `km/h`.
    3. **Steady-Cruise Self-Healing Auto-Sync (`checkSteadyCruiseAutoSync`)**: When holding `W` with Tempomat active at a steady `10 km/h` multiple for `24` ticks (`1.2s`), automatically self-corrects `tempomatSpeedKmh` to match the server's speed cap.

- **Automatic Engine Ignition (`autoMotorStart`)**:
  - When entering a motorized vehicle on GermanMiner, the server initially displays a BossBar with `Motor ausgeschaltet`.
  - When enabled (`config.autoMotorStart = true`), [`TachoModule`](../v26_3/src/main/java/de/pflanzenkaktus/enhancedgm/client/module/TachoModule.java) arms a 4-tick (~200ms) timer to allow vehicle session initialization, then automatically dispatches the `/vehicles motor` command.
  - State tracking (`hasAttemptedMotorStart`) ensures ignition is dispatched once upon entering and never re-ignites against the player's intent if stopped manually while parked. Dismounting resets the state for the next entry.

---

### 2.4 Plot Expiry Reminder ("Grundstücke & Wohnungen", 60-Day Rule)
- **Classes**:
  - Platform-independent (:common): [OwnedPlot](../common/src/main/java/de/pflanzenkaktus/enhancedgm/model/OwnedPlot.java), [PlotTracker](../common/src/main/java/de/pflanzenkaktus/enhancedgm/feature/PlotTracker.java), [PlotRepository](../common/src/main/java/de/pflanzenkaktus/enhancedgm/data/PlotRepository.java)
  - Client implementation (:v26_3): [PlotModule](../v26_3/src/main/java/de/pflanzenkaktus/enhancedgm/client/module/PlotModule.java), [PlotOverviewScreen](../v26_3/src/main/java/de/pflanzenkaktus/enhancedgm/client/screen/PlotOverviewScreen.java), [PlotScanPromptScreen](../v26_3/src/main/java/de/pflanzenkaktus/enhancedgm/client/screen/PlotScanPromptScreen.java)

- **The 60-Day Server Rule**:
  GermanMiner automatically clears and resets rented/purchased plots (mie*, kau*) and apartments if they have not been stepped on for 60 consecutive days (OwnedPlot.EXPIRY_DAYS = 60). The addon calculates exact deadlines: deadlineEpochMs = lastVisitedEpochMs + (60 * 24 * 60 * 60 * 1000L).

- **Automated Menu Scan & Multi-Page Pagination**:
  - Triggered via Settings button (*Scan starten*) or initial prompt dialog (*Jetzt scannen*).
  - Closes any open GUI, dispatches /menu, and waits for the container screen titled Spielmenü.
  - Clicks the item titled Grundstücke & Wohnungen via cross-version click helper [VersionCompat.clickContainerSlot](../v26_3/src/main/java/de/pflanzenkaktus/enhancedgm/client/compat/VersionCompat.java).
  - Waits for Spielmenü (Regionen) and parses every region item's lore (Typ:, x: y: z:, Stadtgebiet:) using [PlotTracker.parseMenuItem](../common/src/main/java/de/pflanzenkaktus/enhancedgm/feature/PlotTracker.java).
  - **Multi-Page Pagination**: Detects pagination navigation items (
ächste, weiter, 
ext, », →, excluding back buttons) and traverses up to 20 pages, accumulating all entries in scanAccum while eliminating duplicates.
  - Safe failure: 200-tick timeout aborts if the menu fails to load. Empty scan results never overwrite or erase existing database entries.

- **Actionbar Visit Detection & Dual Ownership Model**:
  - Whenever the player walks into a plot, GermanMiner displays an action bar message:
    - Personal plot: » Dieses Grundstück ist im Besitz von PflanzenKaktus
    - Company plot: » Diese Mietzone ist im Besitz von SuperChaos (Firma)
  - [PlotTracker.parseOwnerMessage](../common/src/main/java/de/pflanzenkaktus/enhancedgm/feature/PlotTracker.java) strips Minecraft color codes and extracts the owner name and company status.
  - Ownership validation:
    - Normal messages count only if ownerName.equalsIgnoreCase(clientPlayerName).
    - Company messages count only if ownerName.equalsIgnoreCase(config.plotCompanyName) (configured in Settings).
  - **Spatial Proximity Matching**:
    - The player's current (x, z) position is matched against all stored plots within config.plotMatchRadiusBlocks (default 150m, adjustable 20–500m).
    - The closest plot receives an updated lastVisitedEpochMs = System.currentTimeMillis().
    - If multiple plots lie within the radius, the closest counts and a disambiguation chat notification lists the other nearby plots.
    - Anti-spam feedback: Chat confirmation is throttled so walking back and forth across boundaries does not flood chat.

- **Exact Datetime Formatting**:
  - Visits are persisted as precise epoch timestamps.
  - Displayed across chat warnings, overview screens, and HUD widgets with minute-level precision using dd.MM.yyyy HH:mm (e.g. 23.12.2026 19:48).

- **First-Time User Prompting (PlotScanPromptScreen)**:
  - ~5 seconds after connecting to GermanMiner, if no plots are saved in plots.json, a modal dialog appears asking the player to scan their plots.
  - If a scan completes with 0 plots (the player currently owns nothing), the prompt is suppressed for 30 days (config.plotRescanPromptDays = 30) via config.plotLastScanEpochMs.

- **Periodic In-Game Reminders & Dynamic HUD Widget**:
  - **Chat Reminders**: Dispatched ~5 seconds after join and periodically every config.plotReminderIntervalMinutes (default 30 min, configurable from 0 = join-only to 120 min) whenever any plot is expired, expiring within config.plotReminderDaysBefore (slider 1 to 60 days), or has never been visited.
  - **HUD Widget (plot_reminder)**: Displays the most critical plot, countdown days, exact date/time, and color-coded status bar (Green > threshold, Amber <= threshold, Red <= 3 days / expired). Automatically hides when all plots are safe. Movable and scalable in the HUD Layout Editor (H).

- **In-Game Testing Facilities (No 60-Day Wait)**:
  - **Jetzt prüfen (	est_reminder)**: Instantly executes the reminder check in chat and reports either expiring plots or confirms that all plots are safe with the nearest deadline.
  - **Frist auf 2 Tage setzen (simulate_test_plot)**: Sets the first plot's timestamp to 58 days ago (2 days remaining) and triggers HUD and chat warnings immediately. If no plots exist, generates a sample plot kau2007 (Test) for testing.
  - **Auf 60 Tage zurücksetzen (eset_test_plot)**: Restores the test plot's visit timestamp to the current time (full 60-day safety margin).
  - **Threshold Slider**: Configurable up to 60 days so even freshly visited plots can trigger the warning pipeline if desired.

- **Local Storage Architecture (plots.json)**:
  - Persisted at config/gm-addon/plots.json.
  - Grouped strictly by lowercase Minecraft account name:
    `json
    {
      "accounts": {
        "pflanzenkaktus": [
          {
            "name": "kau2007",
            "type": "Grundstück",
            "x": -2742,
            "y": 126,
            "z": -609,
            "area": "Nördlicher Gebirgszug",
            "lastVisitedEpochMs": 1792778000000
          }
        ]
      }
    }
    `
  - Rescan behavior: mergeScan preserves visit timestamps of matching plots (
ame + x, y, z), appends newly discovered plots with lastVisitedEpochMs = 0, and prunes deleted/sold plots.
  - Zero Cloud Footprint: Never uploaded to Firebase or external servers.

---