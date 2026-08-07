# BRD Module 07 — Future Roadmap: 5G NR Sidelink

**Parent Document:** 00-overview.md
**Module Owner:** TBD
**Status:** Draft v1.0 — Research/Not Committed for v1

---

## 1. Purpose

Document the forward-looking requirements for integrating 5G NR Sidelink (3GPP Rel-16+ device-to-device communication) as an additional transport tier, to be added once public Android APIs and carrier/chipset support mature. This module exists so the Transport Abstraction Layer (Module 02) is designed today without needing a rewrite later.

## 2. Why This Is Deferred (Not v1)

- Public Android APIs for app-level Sidelink access are not broadly available as of this writing; access today is largely carrier/OEM-controlled (e.g., public safety use cases), not exposed to third-party consumer apps.
- Chipset support is inconsistent across the current Android device landscape.
- Building against an unstable/inaccessible API would create high maintenance risk for v1.

## 3. Anticipated Benefits (Why It's Worth Planning For)

| Benefit | Detail |
|---|---|
| Range | Potentially 50-100m+ line-of-sight vs Wi-Fi Direct's practical ~10-20m |
| No AP-mode tax | Unlike Wi-Fi Direct, doesn't force one device into access-point role, avoiding Wi-Fi disconnects |
| Cellular-grade interference handling | Better behavior in crowded RF environments (e.g., stadiums, events) than Wi-Fi |

## 4. Draft Functional Requirements (For Future Implementation)

| ID | Requirement | Priority (future) |
|---|---|---|
| FR-07.1 | App shall detect Sidelink capability via Android API once publicly available, and add it as a transport option in Module 02's priority list — above Wi-Fi Aware if benchmarked faster. | Must (future) |
| FR-07.2 | Sidelink negotiation shall reuse the same capability-exchange payload structure as Module 02, extended with a Sidelink-specific capability flag. | Must (future) |
| FR-07.3 | Transfer Engine (Module 03) shall be able to bond Sidelink with Wi-Fi Aware for combined throughput, same as existing dual-Wi-Fi-band bonding (FR-03.4). | Should (future) |

## 5. Monitoring Plan

- Track Android AOSP release notes and Google I/O announcements for Sidelink API exposure.
- Track chipset vendor (Qualcomm, MediaTek, Samsung Exynos) roadmaps for Sidelink hardware support in mid-range/flagship tiers.
- Re-evaluate this module's priority every 2 quarters.

## 6. Design Implication for Current (v1) Modules

- Module 02's Transport Abstraction Layer interface must be designed so a new transport (like Sidelink) can be added as a plugin, without modifying Module 03's chunking/bonding logic.
- No other v1 module should hardcode assumptions that only Wi-Fi/BLE/UWB exist as transports.

## 7. Out of Scope (For Now)

- Any actual implementation work.
- Carrier partnership discussions (would only become relevant if pursuing Sidelink access outside standard public APIs).
