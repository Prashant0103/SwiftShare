# SwiftShare

P2P Android file-sharing app. Full requirements live in [`BRDs/`](BRDs/00-overview.md).

## Module map

| Gradle module | BRD doc | Owns |
|---|---|---|
| `core-security` | `BRDs/05-security-trust-module.md` | Pairing, ECDH keys, session encryption |
| `core-discovery` | `BRDs/01-discovery-module.md` | BLE advertise/scan, UWB ranging |
| `core-transport` | `BRDs/02-transport-negotiation-module.md` | Transport Abstraction Layer (TAL), Wi-Fi Aware/Direct |
| `core-transfer` | `BRDs/03-transfer-engine-module.md` | Chunking, multi-transport bonding, resume, integrity |
| `core-compression` | `BRDs/04-compression-module.md` | Pre-transfer lossless compression |
| `app` | `BRDs/06-ui-ux-module.md` | Compose UI, share-sheet integration |

`BRDs/07-future-roadmap-sidelink.md` (5G NR Sidelink) is doc-only — no v1 code. `core-transport`'s `Transport`/`TransportKind` interface is shaped so a `SidelinkTransport` can be added later without changes to `core-transfer`.

## Build order

`core-security` → `core-discovery` → `core-transport` → `core-transfer` → `core-compression` → `app` UI wiring → NFR hardening.

Security is built first even though the BRD lists it as module 05 — rotating device IDs, capability exchange, and chunk encryption all consume identity/keys from it, so it's a foundation dependency in practice, not a bolt-on.

## Current state

Architectural scaffold only: Gradle multi-module wiring + interfaces, no feature implementations yet. `./gradlew build` should succeed (interface-only modules compile trivially) — this proves the dependency graph, nothing else.

## Known issue to reconcile in the BRDs

`00-overview.md`'s goal metric targets tap→first-byte **< 1.5s** for known devices, but `01-discovery-module.md` (NFR-01.2) allows discovery alone up to 2s, and `02-transport-negotiation-module.md` (NFR-02.1) allows negotiation up to 1s more for known devices. The sub-budgets already exceed the top-level target — needs reconciling before those NFRs are used as acceptance gates.
