# BRD Module 06 — UI/UX

**Parent Document:** 00-overview.md
**Module Owner:** TBD
**Status:** Draft v1.0

---

## 1. Purpose

Deliver a user experience that requires fewer taps and less ambiguity than Nearby Share/AirDrop, surfacing the underlying speed/reliability improvements from Modules 01-05 without exposing technical complexity to the end user.

## 2. Scope

- Share sheet integration (Android system share intent).
- Device selection screen (list and/or directional UWB view).
- Transfer progress screen.
- Pairing confirmation screen.
- Settings (trusted devices, chunk size/advanced settings, visibility).

## 3. Functional Requirements

| ID | Requirement | Priority |
|---|---|---|
| FR-06.1 | App shall register as a target in Android's system share sheet, so users can share from Gallery/Files/any app directly. | Must |
| FR-06.2 | Device selection screen shall show a proximity-sorted list by default; if UWB is available on both ends, show a directional radar-style view instead. | Must |
| FR-06.3 | Selecting a device shall immediately begin transport negotiation (Module 02) in the background while showing a "connecting" state — no separate "connect" button required. | Must |
| FR-06.4 | Transfer progress screen shall show: file name(s), percentage complete, current speed, ETA, and which transport is active (e.g., "Wi-Fi Aware" badge) for transparency. | Must |
| FR-06.5 | Pairing confirmation (first-time devices) shall use a large, glanceable numeric/emoji code with a single "Confirm" / "Reject" button pair. | Must |
| FR-06.6 | Settings screen shall list previously-paired devices with a "Remove" action per device (feeds Module 05, FR-05.5). | Must |
| FR-06.7 | App shall show a persistent but unobtrusive notification during active transfer, allowing background operation while the user uses other apps. | Should |
| FR-06.8 | On transfer completion, receiver shall get a system notification with a direct "Open" action (e.g., open the received photo/file). | Must |
| FR-06.9 | App shall support drag-and-drop / multi-select file and folder picking, feeding Module 03's batch-job support (FR-03.7). | Should |

## 4. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-06.1 | Total taps from "share intent invoked" to "transfer started" for a previously-paired device shall be ≤ 2. |
| NFR-06.2 | UI shall never show raw technical error codes to the user; all failures shall map to plain-language messages with a suggested next action. |
| NFR-06.3 | App shall follow Material Design guidelines for consistency with the rest of the Android system UI. |
| NFR-06.4 | Directional radar view (UWB) shall update at a minimum of 5Hz for smooth pointing feedback. |

## 5. User Stories

- **US-06.1:** As a user, I want to share a photo the same way I already share to WhatsApp — via the system share sheet.
- **US-06.2:** As a user with a UWB phone, I want to just point at my friend rather than scroll a list.
- **US-06.3:** As a user, I want clear feedback on transfer speed and time remaining, not just a spinning progress bar.
- **US-06.4:** As a user, I want to keep using my phone normally while a large transfer happens in the background.

## 6. Acceptance Criteria

- Given a previously-paired device is nearby, the full flow (share → select device → transfer starts) completes in ≤ 2 taps, measured via UI test.
- Given a first-time device pairing, confirmation screen displays a matching code on both devices and blocks transfer until confirmed on both.
- Given an active transfer, the app remains functional and shows accurate live progress if the user backgrounds it and returns.
- Given a transfer failure (any module), the user sees a plain-language message (e.g., "Connection lost — retrying on Wi-Fi Direct" rather than a raw exception/error code).

## 7. Quick Settings Tile & Scrollable Panel (Nearby Share-style Entry Point)

This is a second, OS-level entry point into the app — in addition to the system share sheet (Section 3) — modeled on Nearby Share's Quick Settings tile and sliding panel.

### 7.1 Functional Requirements

| ID | Requirement | Priority |
|---|---|---|
| FR-06.10 | App shall register a custom Quick Settings tile (`TileService`) that users can add to their notification shade. | Must |
| FR-06.11 | Tapping the tile shall open a scrollable bottom-sheet panel (not the full app) showing nearby discoverable devices, reusing Module 01's discovery logic. | Must |
| FR-06.12 | The panel shall have two modes accessible via tabs or a toggle: **Send** (browse files, then pick a device) and **Receive** (toggle "make my device visible" on/off). | Must |
| FR-06.13 | The tile's icon/state shall visually reflect current status (idle, visible/discoverable, transfer in progress). | Should |
| FR-06.14 | The panel shall support the same directional/UWB view as the full-app device selector (FR-06.2) when available. | Should |
| FR-06.15 | Selecting a device in Send mode from the panel shall open the system file picker, then proceed directly into the standard negotiation/transfer flow (Modules 02-03), same as the share-sheet path. | Must |
| FR-06.16 | Toggling "visible" in Receive mode from the panel shall control the same discoverability state used in Module 01 (FR-01.1) — no separate/duplicate discoverability logic. | Must |
| FR-06.17 | The panel shall be dismissible by swiping down or tapping outside it, without interrupting an in-progress transfer (transfer continues in background per FR-06.7). | Must |

### 7.2 Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-06.5 | Panel shall open within 300ms of tile tap (perceived-instant), independent of discovery completion time. |
| NFR-06.6 | Panel's device list shall populate progressively as devices are discovered (not block on a fixed scan timeout), consistent with Module 01's real-time discovery events. |
| NFR-06.7 | Tile and panel shall follow Android's Quick Settings design guidelines so it looks native to the OS, not like a custom overlay. |

### 7.3 User Stories

- **US-06.5:** As a user, I want to pull down Quick Settings and tap a tile to instantly start sharing, without opening the full app.
- **US-06.6:** As a user, I want a quick way to toggle "make my device visible" without digging through app settings.
- **US-06.7:** As a user, I want the quick panel to feel as fast and native as Nearby Share's, not like a slow web view.

### 7.4 Acceptance Criteria

- Given the tile is added to Quick Settings, tapping it opens the panel in ≤ 300ms.
- Given the panel is open in Send mode, selecting a device and a file starts transfer using the same negotiation path as the share-sheet flow (verified: identical Module 02/03 events fired).
- Given a transfer is in progress and the user dismisses the panel, the transfer continues uninterrupted and progress is reflected in the persistent notification (FR-06.7).
- Given Receive mode toggle is switched on from the panel, the device becomes discoverable to others per Module 01, without requiring the main app to be open.

## 8. Dependencies

- Consumes state/events from Modules 01–05 but owns no transport or security logic itself.
- Depends on Android `Intent.ACTION_SEND` / `ACTION_SEND_MULTIPLE` for share-sheet integration.
- Depends on Android `TileService` API (Quick Settings) for Section 7's panel entry point.

## 9. Out of Scope

- Desktop/web companion UI (Android-only in v1).
- Customizable theming/branding beyond standard Material Design in v1.
- Lock-screen-level sharing (i.e., initiating transfer without unlocking the device) — not addressed in v1 for security reasons (ties to Module 05 trust model).
