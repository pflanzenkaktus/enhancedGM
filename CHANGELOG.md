# Changelog

All notable changes to the **enhancedGM** (GermanMiner Addon) project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.3.0] - 2026-10-25

### Added
- **Automatic Update Checker & Opt-In Dialog**:
  - Asynchronous background check against GitHub Releases API on game startup and server join.
  - Interactive chat notification when a newer release is published, complete with direct clickable `[Download]` and `[Changelog]` links with hover tooltips.
  - Setup dialog displayed on first launch: asks for confirmation before checking GitHub for updates.
  - Configurable toggle in the *General* settings tab and manual "Check for Updates Now" action in the *Debug & Advanced* menu.
- **Automatic Engine Start (Auto Motor Start)**:
  - Automatically executes `/vehicles motor` upon mounting motorized vehicles whenever the BossBar indicates the engine is switched off.
  - Smart vehicle filter prevents accidental triggers on non-motorized entities (bicycles, skateboards, wheelchairs, horses, camels, etc.).
  - Configurable toggle in the settings screen under *Speedometer & Cruise Control*.
- **Plot & Apartment Expiration Reminder System (Plot Tracker)**:
  - Expropriation protection: Prevents automatic clearing of owned plots and apartments caused by GermanMiner's 60-day inactivity rule.
  - Multi-page GUI menu scanner for `/p a` and region menus to automatically record and catalog all owned plots and apartments.
  - Live boundary tracking: Automatically updates the deadline timestamp upon entering your own plot via Chat and ActionBar message detection (`Du betrittst das Grundstück...`).
  - Fullscreen overview screen (`PlotOverviewScreen`) with sortable table view by expiration date, remaining days, and urgency color codes.
  - Movable HUD Widget (`kau2007 (Grundstück) - 12 days left`) with configurable reminder threshold and repeat intervals.
- **Dedicated "Debug & Advanced" Settings Menu (`DebugModule`)**:
  - Clean separation: All simulation, test, and developer-level calibration tools have been moved from standard module views into a dedicated category.
  - Includes: Spawn protection timer simulation (20s), plot reminder test trigger, plot expiration simulation (2 days left) and reset, server domain regex pattern filter, developer override (`forceEnableEverywhere`), speedometer and speed camera calibration factors, and BlueMap tile cache TTL management with instant cache wipe.

### Fixed
- **Pixel-Perfect HUD Hitbox & Selection Frame Alignment**:
  - Resolved sizing discrepancies where rendered widget cards extended beyond the HUD editor selection boxes and click boundaries (`isPointInside`).
  - Standardized Spawn Protection widget dimensions to a fixed 210 px width with automatic label clamping, eliminating a prior 35 px overflow.
- **Minimap Viewport & Responsive Text Containment**:
  - Restored full edge-to-edge tile rendering across the entire minimap viewport without inner borders or scissor misalignment.
  - Dynamic font scaling for coordinates and current speed in Line 1 under compact minimap sizes (prevents horizontal overflow past card borders).
  - Multi-tier graceful degradation for shortcut badges (`[M] Map [H] HUD [O] Opt.` -> `[M] Map [H] HUD` -> `[M] Map` -> `[M]`) with automatic scaling and vertical omission at ultra-compact scales.
  - Clamped Cardinal North (N) compass badge to remain strictly inside minimap boundaries.
- **Speedometer Text Clamping & Polish**:
  - Dynamically scaled cruise control and fuel percentage status texts in analog and digital dashboard modes to prevent horizontal text clipping.
  - Replaced HUD emojis with clean, standard-compliant text badges (`Tank XX%`).

---

## [0.2.5] - 2026-10-23

### Fixed
- **Speedometer Vehicle State Detection (Fixes #2)**:
  - Fixed an issue where the speedometer appeared when sitting on the ground (e.g. via `/sit`).
  - Added strict verification requiring both player vehicle mounting and active server vehicle BossBar presence.

---

## [0.2.4] - 2026-10-22

### Added
- **Initial Modular Release**:
  - **Speed Camera Alert System**: Audio chimes, 3D world markers, HUD warning cards, and opt-in Firebase cloud synchronization.
  - **Speedometer & Cruise Control**: Switchable Analog and Digital dashboards calibrated to server km/h with rate-limited cruise control shortcuts.
  - **BlueMap Live Minimap & Fullscreen Map**: GPU-accelerated tile streaming from map.germanminer.de with POI markers, rotation modes, and zoom controls.
  - **Spawn Protection Timer**: 20-second countdown bar for server logins, respawns, and hospital discharges.
  - **Magnetic HUD Layout Editor**: Visual drag-and-drop HUD arrangement with edge snapping and scale adjustments.
  - **Cross-Version Architecture**: Full compatibility with Minecraft 26.3 and extensible modular design for 26.1.
