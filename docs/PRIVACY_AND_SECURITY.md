# Privacy & Security Technical Overview

This document provides a comprehensive, transparent, and verifiable overview of data handling, network communication, storage locations, and security policies in the **GermanMiner Addon (`enhancedGM`)**.

---

## 1. Network Communications & Transport Layer Notice

Like any software connecting to services on the public internet, outgoing network connections inherently expose the client's public IP address at the **TCP/IP transport layer** to the receiving server infrastructure.

| Service | Host | Protocol | Transport Layer Processing |
| :--- | :--- | :--- | :--- |
| **GermanMiner BlueMap** | `map.germanminer.de:2086` | HTTP GET | Standard web server access logs by the GermanMiner server host when downloading map tiles and POI markers. |
| **Firebase Cloud Sync** | `*.firebasedatabase.app` | HTTPS REST (TLS 1.3) | Standard Google Cloud infrastructure edge routing and security logging pursuant to Google's standard enterprise privacy and security disclosures. |

**Client Data Scope**:
* The addon application code does not query, collect, inspect, or serialize the player's IP address or hardware MAC address.
* No IP address is written to `config/gm-addon/speed_cameras.json` or uploaded to any Firebase database field.

---

## 2. Speed Camera Community Cloud Sync (Firebase)

The addon includes a community-driven database for Speed Camera ("Blitzer") locations on GermanMiner.

### 2.1 Exact Payload Structure
When a speed camera is detected from an official server chat notification (`"Du wurdest mit ... km/h in einer ... km/h-Zone geblitzt..."`) and cloud synchronization is enabled, the addon uploads only the following structured JSON document:

```json
{
  "id": "auto_-2131_-1436",
  "name": "Blitzer 70er (-2131, -1436)",
  "x": -2131.3,
  "y": 65.0,
  "z": -1436.1,
  "speedLimitKmh": 70,
  "directionDegrees": 0.0,
  "triggerRadius": 0.0,
  "autoAdded": true,
  "addedBy": "community",
  "createdAtEpochMs": 1792283552105
}
```

### 2.2 Data That is Not Collected or Stored
* **No Authentication Credentials**: No passwords, session tokens, or Mojang/Microsoft account tokens are accessed or transmitted.
* **No Continuous Telemetry or GPS Movement Trails**: The addon does not track or stream player coordinates during normal driving, walking, or flying. Coordinates are only sampled at the exact location of a speed-camera flash.
* **No Private Messages or Inventory**: Chat messages between players, inventory contents, bank balances, and other gameplay actions are never collected or transmitted.
* **Sanitized Inputs**: The `addedBy` field is sanitized strictly to alphanumeric characters and underscores (`[a-zA-Z0-9_]`) with a maximum length cap to prevent payload injection.

### 2.3 Retention Policy & Complete Anonymity by Design
* **Zero Username Storage**: Speed cameras are recorded with the generic attribution `"community"`. Individual player in-game names are never stored or transmitted to the remote database.
* **Camera Coordinate Retention**: Physical speed-camera coordinates (`x, y, z`, speed limit) are maintained in the community pool as long as the camera is active on the server.
* **Local Data Purging**: Players can purge their local copy of the database at any time by deleting `config/gm-addon/speed_cameras.json`.

---

## 3. Database Access Controls & Integrity Rules

The Firebase Realtime Database is configured with structured validation rules to maintain data integrity and prevent unauthorized mass modifications:

* **Read Access**: Open (`".read": true`) allowing any client to download verified camera coordinates.
* **Write & Append Rules**:
  * New entries must validate against schema types (`x`, `y`, `z` as numbers; `speedLimitKmh` as an integer between `10` and `140`).
  * Coordinates must fall within the valid GermanMiner world boundaries (`-10000` to `10000`).
  * `addedBy` string length must not exceed 24 characters.
  * Root database replacement or bulk record deletion by unauthorized clients is strictly denied.
* **Client-Side Spatial Deduplication**:
  * The client enforces a 28-meter horizontal radius deduplication (`blitzerDedupeRadiusBlocks`) to prevent duplicate cameras from being created or stacked at the same intersection.

---

## 4. Local Data Storage & User Sovereignty

* **Plain JSON Storage**: All camera data is stored locally in human-readable JSON format at `config/gm-addon/speed_cameras.json`.
* **Full Data Sovereignty**: Users can inspect, back up, edit, or delete `speed_cameras.json` at any time. Deleting the file completely purges the local database.
* **Settings Toggle**: Community Cloud Sync can be toggled ON or OFF at any time in the In-Game Settings (`O` or Fabric Mod Menu) under *Blitzer-Warner & Cloud*.

---

## 5. Local Network & Port Policy

* **Zero Local Open Ports**: The addon does not host or bind any local HTTP servers, background web bridges, or listening network sockets on `localhost` (`127.0.0.1`) or `0.0.0.0`.
* **Client-Only Architecture**: All HUD rendering, countdown timers, and speed calculations occur strictly in-memory on the client thread.

---

## 7. Plot Reminder Data (local only)

The plot expiry reminder stores your own plots and apartments (name, type, coordinates, city area, last verified visit time) in `config/gm-addon/plots.json`, separated by Minecraft account name. This data is read from the server menu you open yourself via the Settings scan, and from action bar messages. It is **never uploaded** to Firebase or any other service. Deleting `plots.json` purges it completely. The optional company name is stored locally in `config/gm-addon/config.json`.
---

## 6. Disclaimer & Limitation of Liability

enhancedGM is an experimental, private community hobby project provided strictly "AS IS", without warranty of any kind, express or implied. The authors do not guarantee uninterrupted availability, error-free operation, or absolute data security. To the maximum extent permitted by applicable law, the authors explicitly disclaim all liability for software defects, in-game penalties, data loss, or potential privacy, security, or data-handling flaws and misconfigurations. Use of this addon and all network synchronization is entirely voluntary and at the user's sole risk.
