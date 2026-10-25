# Changelog

All notable changes to the **enhancedGM** (GermanMiner Addon) project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.3.0] - 2026-10-25

### Added
- **Automatischer Motorstart (Auto Engine Start)**:
  - Automatisches Starten des Motors via `/vehicles motor` beim Einsteigen in motorisierte Fahrzeuge, sobald die BossBar den Motor als ausgeschaltet signalisiert.
  - Intelligenter Filter schließt nicht-motorisierte Einheiten (Fahrräder, Skateboards, Rollstühle, Pferde, Kamele etc.) zuverlässig aus.
  - Ein-/Ausschaltbar in den Einstellungen unter *Tacho & Tempomat*.
- **Grundstücks- & Wohnungs-Erinnerungs-System (Plot Tracker)**:
  - Schutz vor Enteignung: Verhindert das automatische Löschen/Clearen von Grundstücken und Wohnungen nach 60 Tagen Inaktivität.
  - Multi-Page GUI-Scanner für `/p a` und das Regions-Menü zum automatischen Erfassen aller Grundstücke und Wohnungen.
  - Automatische Frist-Aktualisierung beim Betreten eigener Grundstücke via Chat- und ActionBar-Erkennung (`Du betrittst das Grundstück...`).
  - Neue Vollbild-Übersicht (`PlotOverviewScreen`) mit Sortierung nach Ablaufdatum, Resttagen und Dringlichkeits-Farben.
  - Eigenes, verschiebbares HUD-Widget (`kau2007 (Grundstück) - 12 Tage verbleibend`) mit konfigurierbarem Vorwarn-Schwellenwert.
- **Neues Einstellungsmenü „Debug & Erweitert“ (`DebugModule`)**:
  - Sämtliche Entwickler-, Test- und Simulations-Funktionen wurden aus den regulären Modul-Ansichten in ein eigenes Menü gebündelt.
  - Enthält: Spawnschutz-Simulation (20s), Grundstücks-Erinnerungstest, Ablauf-Simulation (2 Tage Restzeit) & Reset, Server-Domain-Regex-Filter, Entwickler-Override (`forceEnableEverywhere`), Tacho- & Blitzer-Kalibrierung sowie BlueMap Tile-Cache TTL und Sofort-Reload.

### Fixed
- **Pixelgenaue HUD-Hitboxen & Bounding-Boxen**:
  - Selektionsrahmen im HUD-Editor und Mausklick-Erkennungsbereiche (`isPointInside`) stimmen nun pixelgenau mit den gezeichneten Widget-Karten überein.
  - Spawnschutz-Widget auf eine feste Basisbreite von 210 px mit automatischem Label-Clamping standardisiert (behob eine Abweichung von bis zu 35 px zwischen Karte und Rahmen).
- **Minimap Viewport & Text-Containment**:
  - Vollflächiges Kachel-Rendering im gesamten Minimap-Quadrat ohne innere Ränder oder Scissor-Versatz wiederhergestellt.
  - Dynamische Schriftgrößen-Skalierung für Koordinaten und Tempo in Zeile 1 bei kleinen Kartengrößen (verhindert horizontales Überstehen).
  - Mehrstufige Kürzungslogik für Hotkey-Hinweise (`[M] Karte [H] HUD [O] Opt.` -> `[M] Karte [H] HUD` -> `[M] Karte` -> `[M]`) inklusive vertikaler Ausblendung bei starker Verkleinerung.
  - Die Kompassnadel (N) bleibt nun dauerhaft innerhalb der Minimap-Grenzen geklammert.
- **Tacho Text- & UI-Polishing**:
  - Tempomat- und Tankstatus-Texte in der analogen und digitalen Anzeige skalieren sich automatisch, damit sie selbst bei hohen Werten das Gehäuse nicht verlassen.
  - HUD-Emoji durch standardkonformen Klartext (`Tank XX%`) ersetzt.

---

## [0.2.5] - 2026-10-23

### Fixed
- **Tacho-Sitzpositions-Erkennung (Fixes #2)**:
  - Der Tacho erscheint nicht mehr beim einfachen Hinsetzen auf den Boden (z. B. via `/sit`).
  - Prüfung erweitert: Anzeige nur noch, wenn der Spieler tatsächlich in einem Fahrzeug sitzt und die offizielle Fahrzeug-BossBar aktiv ist.

---

## [0.2.4] - 2026-10-22

### Added
- **Initial Modular Release**:
  - **Blitzer-Warnsystem**: Automatische Warnung vor Geschwindigkeitskameras (akustisch, 3D-Weltmarker, HUD-Badge & opt-in Firebase Cloud-Sync).
  - **Tacho & Tempomat**: Analoge Rundanzeige und modernes digitales Dashboard mit automatischer Skalierung bis 320 km/h, Tank-Status und Tempomat-Shortcuts.
  - **BlueMap Live-Minimap & Fullscreen-Karte**: GPU-beschleunigte Kacheldarstellung von map.germanminer.de mit Spieler-Ausrichtung, POI-Markern (Tankstellen, Taxis, Bus, S-Bahn) und Zoom.
  - **Spawnschutz-Timer**: 20-Sekunden-Countdown bei Login, Respawn und Krankenhaus-Entlassung.
  - **Magnetischer HUD-Editor**: Frei verschiebbare und stufenlos skalierbare Widgets mit automatischer Raster- und Kanten-Ausrichtung.
  - **Cross-Version Architektur**: Vollständig kompatibel mit Minecraft 26.3 und vorbereitet für 26.1.
