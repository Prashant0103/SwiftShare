# BRD Module 01 — Discovery

**Parent Document:** 00-overview.md
**Module Owner:** TBD
**Status:** Draft v1.0

---

## 1. Purpose

Enable a phone to find nearby devices running the app, with minimal battery cost and minimal latency, and (where hardware allows) disambiguate *which* nearby device the user intends to send to using directional ranging.

## 2. Scope

- BLE advertising and scanning for app-instance discovery.
- Optional UWB ranging for direction/distance disambiguation.
- Background "pre-warm" discovery for previously-paired devices.

## 3. Functional Requirements

| ID | Requirement | Priority |
|---|---|---|
| FR-01.1 | App shall advertise its presence via BLE when the share sheet/app is open. | Must |
| FR-01.2 | App shall scan for other advertising instances of the app within BLE range (~10-30m typical). | Must |
| FR-01.3 | Each advertisement shall include a device capability payload: supported transports (Wi-Fi Aware, Wi-Fi Direct, UWB), app version, and a rotating anonymous device ID (not a persistent hardware identifier). | Must |
| FR-01.4 | If both devices support UWB, app shall initiate UWB ranging after BLE discovery to obtain relative distance and angle. | Should |
| FR-01.5 | App shall maintain a local list of previously-paired device IDs (hashed, stored on-device only) to enable faster re-discovery. | Must |
| FR-01.6 | When app is opened, it shall proactively re-establish BLE contact with the 5 most recently used devices in the background, before the user selects a file. | Should |
| FR-01.7 | Discovery shall time out and stop scanning after 60 seconds of inactivity to conserve battery. | Must |
| FR-01.8 | User shall be able to manually refresh/re-scan. | Must |

## 4. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-01.1 | BLE scanning shall use Android's low-latency scan mode only while the share UI is in foreground; balanced/low-power mode otherwise. |
| NFR-01.2 | Discovery-to-first-result latency shall be < 2 seconds in typical indoor conditions for devices within 10m. |
| NFR-01.3 | No device shall be discoverable unless the app is actively opened by the user (no silent background broadcasting for privacy). |
| NFR-01.4 | Rotating device IDs shall change every session to prevent long-term tracking by third parties sniffing BLE traffic. |

## 5. User Stories

- **US-01.1:** As a user, I want to open the app and immediately see nearby friends' devices, so I don't wait around.
- **US-01.2:** As a user with a UWB-capable phone, I want to point my phone at my friend to select their device, instead of picking from an ambiguous list.
- **US-01.3:** As a returning user, I want the app to recognize my frequently-shared-with contacts faster than new/unknown devices.

## 6. Acceptance Criteria

- Given two app instances open within 10m, both shall appear in each other's discovery list within 2 seconds.
- Given UWB hardware on both ends, the UI shall display a directional indicator (angle + distance) rather than a plain list.
- Given no UWB hardware, the UI shall gracefully fall back to a plain proximity-sorted list (by BLE RSSI) with no error shown to the user.

## 7. Dependencies

- Requires Android `BluetoothLeAdvertiser` / `BluetoothLeScanner` APIs.
- UWB ranging requires Android's `UwbManager` (API 33+) — feature-detected via `PackageManager.hasSystemFeature(FEATURE_UWB)`.

## 8. Out of Scope

- Discovery across the open internet (this module is proximity-only, not account/cloud based).
- Discovery of non-app devices (e.g., generic Bluetooth peripherals).
