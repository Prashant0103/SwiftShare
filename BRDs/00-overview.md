# BRD: Fast Device-to-Device File Sharing App (Android)
## Document 00 — Overview & Index

**Project Codename:** SwiftShare (placeholder — rename as needed)
**Platform:** Android (5G-capable devices, with graceful fallback for older hardware)
**Document Owner:** Prashant
**Status:** Draft v1.0

---

## 1. Purpose

Define business and functional requirements for a peer-to-peer (P2P) Android file-sharing application that is **faster and more convenient than Google Nearby Share**, with zero backend infrastructure cost, using a tiered multi-radio transport strategy (BLE, UWB, Wi-Fi Aware, Wi-Fi Direct, with 5G Sidelink reserved for future roadmap).

## 2. Business Problem

- Existing solutions (Nearby Share, AirDrop, Xender, ShareIt) are either platform-locked, slow to negotiate a connection, degrade the user's existing Wi-Fi connection, or bundle ads/cloud relays that raise privacy concerns.
- Users transferring large files (photos, videos, documents) between Android devices experience noticeable connection setup delay (3–8 seconds) and inconsistent throughput.
- There is no free, open, privacy-respecting app that dynamically picks the best available radio and bonds multiple radios for one transfer.

## 3. Goals & Success Metrics

| Goal | Metric | Target |
|---|---|---|
| Reduce connection setup time | Time from "tap send" to first byte transferred | < 1.5s for known devices, < 4s for new devices |
| Increase throughput | Effective transfer speed for large files (>500MB) | ≥ Nearby Share baseline; stretch goal +30% via radio bonding |
| Improve reliability | Transfer success rate without manual restart | ≥ 99% (via chunk-level resume) |
| Zero infra cost | Backend hosting cost | ₹0 (fully P2P, no server) |
| Ease of use | Taps required from file selection to transfer start | ≤ 2 taps for previously-paired devices |

## 4. Scope

**In scope (v1):**
- Android-to-Android transfer only
- BLE-based discovery
- Wi-Fi Aware (NAN) as primary transport, Wi-Fi Direct as fallback
- UWB-assisted device selection (on supported hardware only — feature-detected, not required)
- Chunk-based transfer engine with resume and integrity verification
- Content-aware compression
- Local trust/pairing model (no cloud accounts)

**Out of scope (v1):**
- iOS support / cross-platform transfer
- 5G NR Sidelink (research/future roadmap — see Module 07)
- Cloud backup, cloud relay, or account-based sharing
- Group/multi-device simultaneous transfer (candidate for v2)

## 5. Module Index

This BRD is split into module-wise documents for independent development and review:

| # | Module | File | Owns |
|---|---|---|---|
| 01 | Discovery | `01-discovery-module.md` | Finding nearby devices (BLE + optional UWB) |
| 02 | Transport Negotiation | `02-transport-negotiation-module.md` | Choosing and establishing the fastest available radio link |
| 03 | Transfer Engine | `03-transfer-engine-module.md` | Chunking, multi-radio bonding, resume, integrity |
| 04 | Compression | `04-compression-module.md` | Content-aware pre-transfer compression |
| 05 | Security & Trust | `05-security-trust-module.md` | Pairing, encryption, permissions |
| 06 | UI/UX | `06-ui-ux-module.md` | User-facing flows and screens |
| 07 | Future Roadmap — 5G Sidelink | `07-future-roadmap-sidelink.md` | Forward-looking, not committed for v1 |

## 6. High-Level Architecture Reference

```
┌─────────────────────────────────────────┐
│         App Layer (UI, file picker)      │   → Module 06
├─────────────────────────────────────────┤
│      Transport Abstraction Layer (TAL)   │   → Module 02
├──────┬──────┬──────┬──────┬─────────────┤
│ BLE  │ Wi-Fi│ Wi-Fi│ 5G   │ UWB          │   → Module 01, 02, 07
│(disc-│Aware │Direct│Side- │ (ranging)    │
│overy)│(NAN) │(fall-│link* │              │
│      │      │back) │(v2+) │              │
├──────┴──────┴──────┴──────┴─────────────┤
│      Transfer Engine (chunk/bond/resume) │   → Module 03
├─────────────────────────────────────────┤
│      Compression + Security Layer        │   → Module 04, 05
└─────────────────────────────────────────┘
```

## 7. Assumptions

- Target devices run Android 10+ (API 29+) for Wi-Fi Aware support; app degrades gracefully on older versions to Wi-Fi Direct only.
- UWB and 5G Sidelink are hardware-dependent and feature-detected at runtime — never assumed present.
- No monetization in v1; app is free, no ads, no cloud accounts.

## 8. Dependencies Across Modules

- Module 02 depends on Module 01 (discovery must complete before negotiation).
- Module 03 depends on Module 02 (transport must be established before transfer starts).
- Module 04 and 05 are cross-cutting — invoked by Module 03 but specified independently.
- Module 06 consumes state/events from all other modules but owns no transport logic itself.

## 9. Out-of-Scope Clarifications

- This BRD does not cover Play Store publishing/monetization strategy.
- This BRD does not cover iOS interoperability; that would require a separate cross-platform BRD given AirDrop's closed protocol.
