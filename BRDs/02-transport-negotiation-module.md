# BRD Module 02 — Transport Negotiation

**Parent Document:** 00-overview.md
**Module Owner:** TBD
**Status:** Draft v1.0

---

## 1. Purpose

Once two devices discover each other (Module 01), determine the fastest mutually-supported transport(s) and establish the connection(s) with minimal setup latency — ideally in parallel with discovery rather than after it.

## 2. Scope

- Capability exchange between devices.
- Transport selection logic (priority order / tiered fallback).
- Parallelized handshake to reduce setup latency.
- Multi-transport negotiation when radio bonding is possible (feeds Module 03).

## 3. Functional Requirements

| ID | Requirement | Priority |
|---|---|---|
| FR-02.1 | Upon discovery, devices shall exchange a capability manifest over the existing BLE link: supported transports, current radio load/battery state, and app version. | Must |
| FR-02.2 | App shall select transport using this priority order: (1) Wi-Fi Aware if both support it, (2) Wi-Fi Direct if not, (3) BLE-only for very small payloads (e.g., text/contact card) as last resort. | Must |
| FR-02.3 | If both devices support Wi-Fi Aware AND a secondary concurrent Wi-Fi band, app shall negotiate a bonded dual-path connection for files above a configurable size threshold (default 100MB). | Should |
| FR-02.4 | Wi-Fi Direct/Aware socket setup shall begin speculatively as soon as capability exchange completes, without waiting for explicit user confirmation of file selection (pending user consent already granted at pairing time). | Should |
| FR-02.5 | If the primary chosen transport fails to establish within 5 seconds, app shall automatically fall back to the next transport in priority order without requiring user intervention. | Must |
| FR-02.6 | Negotiation state (connecting, connected, failed, fallback-in-progress) shall be exposed to the UI layer (Module 06) in real time. | Must |

## 4. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-02.1 | Total negotiation time (from capability exchange start to transport-ready) shall be < 1 second for previously-paired devices, < 3 seconds for first-time pairing. |
| NFR-02.2 | Wi-Fi Aware/Direct negotiation shall not disconnect or degrade the user's existing internet Wi-Fi connection where Wi-Fi Aware is used. Wi-Fi Direct fallback shall clearly warn the user that Wi-Fi may briefly disconnect. |
| NFR-02.3 | Negotiation logic shall be implemented as a Transport Abstraction Layer (TAL) — an internal interface so new transports (e.g., 5G Sidelink in a future release) can be added without rewriting Module 03. |

## 5. User Stories

- **US-02.1:** As a user, I want the app to just pick the best connection method automatically, without asking me technical questions.
- **US-02.2:** As a user on an older phone without Wi-Fi Aware, I still want transfers to work via Wi-Fi Direct without extra setup steps.
- **US-02.3:** As a user sending a large video, I want the app to use every available fast radio at once if that speeds things up.

## 6. Acceptance Criteria

- Given both devices support Wi-Fi Aware, negotiation completes without any visible Wi-Fi disconnect/reconnect notification to the user.
- Given the primary transport fails, the app automatically retries with the next-priority transport within 5 seconds, with no user-visible error unless all transports fail.
- Given a file over 100MB and dual-band capability on both devices, transfer engine (Module 03) receives two active transport handles instead of one.

## 7. Dependencies

- Depends on Module 01 for device identity and capability manifest exchange.
- Feeds Module 03 with one or more established transport handles.
- Android APIs: `WifiAwareManager`, `WifiP2pManager`.

## 8. Out of Scope

- 5G NR Sidelink negotiation — deferred to Module 07 (future roadmap) pending public API availability.
- Cross-platform (Android-iOS) transport negotiation.
