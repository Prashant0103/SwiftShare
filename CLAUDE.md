# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

SwiftShare: Android P2P file-sharing app (BLE/UWB/Wi-Fi Aware/Wi-Fi Direct), zero backend. Full requirements live in `BRDs/00-overview.md` and `BRDs/01` through `BRDs/07` (one doc per module). Read the relevant BRD doc before touching a module — each has functional requirements (FR-XX.Y), non-functional requirements (NFR-XX.Y), and acceptance criteria that define correctness, not just the code.

Repo is a Gradle multi-module Android project, currently at the **architectural scaffold stage**: interfaces and module wiring exist, feature implementations do not yet.

## Build commands

Requires JDK 17 and Android SDK (platform 34, build-tools 34.0.0) — set `JAVA_HOME` and `sdk.dir` in `local.properties` if not already configured on the machine.

```bash
./gradlew build                          # full build: compile + lint + unit tests, all modules
./gradlew :app:build                     # build just the app module (and its dependencies)
./gradlew :core-security:build           # build a single core module
./gradlew test                           # unit tests only, all modules
./gradlew :core-transfer:testDebugUnitTest --tests "com.swiftshare.core.transfer.SomeTest"  # single test
./gradlew lintDebug                      # Android lint only
./gradlew :app:dependencies --configuration debugCompileClasspath   # inspect a module's dependency graph
```

On Windows, use `./gradlew.bat` in place of `./gradlew` if invoking from PowerShell/cmd rather than a POSIX shell.

Lint failures fail the build (not just warnings) — e.g. `ACCESS_FINE_LOCATION` requires `ACCESS_COARSE_LOCATION` alongside it on Android 12+. Fix lint errors rather than suppressing them.

## Architecture

Six Gradle modules, one per BRD module, with a **strict one-directional dependency graph** — never add a reverse dependency (e.g. `core-security` must never depend on `core-transport`):

```
:app
 ├─→ core-transfer   (chunking, multi-transport bonding, resume, integrity — BRD 03)
 │    ├─→ core-transport   (Transport Abstraction Layer: Wi-Fi Aware/Direct — BRD 02)
 │    │    ├─→ core-discovery   (BLE advertise/scan, UWB ranging — BRD 01)
 │    │    │    └─→ core-security
 │    │    └─→ core-security
 │    ├─→ core-compression   (lossless pre-transfer compression — BRD 04)
 │    └─→ core-security   (pairing, ECDH keys, session encryption — BRD 05)
 └─→ (all core-* modules directly, for UI wiring — BRD 06)
```

`core-security` is the foundation leaf module — it has zero project dependencies. Even though the BRD numbers it "05", build/implement it first: rotating device IDs (Module 01), capability exchange (Module 02), and chunk encryption (Module 03) all consume identity/keys from it.

`BRDs/07-future-roadmap-sidelink.md` (5G NR Sidelink) is doc-only, no code. The `Transport`/`TransportKind` interface in `core-transport` is intentionally shaped so a `SidelinkTransport` can be added later without touching `core-transfer`'s chunking/bonding logic — preserve that when adding transports.

### Module responsibilities (interfaces currently defined, no implementations yet)

- **`core-security`**: `PairingManager`, `SessionCrypto`, `KeyStore`. `RotatingDeviceId` value class — never pass a persistent hardware identifier (IMEI/MAC) anywhere in discovery or negotiation payloads (FR-05.4).
- **`core-discovery`**: `DeviceScanner`, `DeviceAdvertiser`, `DiscoveredDevice`/`DeviceCapabilities`/`RangingInfo`.
- **`core-transport`**: `Transport`, `TransportHandle`, `TransportManager`, `NegotiationResult`/`NegotiationState`. Priority order for transport selection is Wi-Fi Aware → Wi-Fi Direct → BLE-only (FR-02.2) — Wi-Fi Aware fragmentation across chipsets is real even on API 29+ devices, so don't assume availability without runtime feature detection.
- **`core-transfer`**: `TransferEngine`, `ChunkScheduler`, `TransferJob` (default chunk size 4MB per FR-03.1), `TransferProgress`, `ChunkResult`.
- **`core-compression`**: `CompressionStrategy` + `NoOpCompressionStrategy` default. Compression must stay lossless (FR-04.4) and skip already-compressed formats (FR-04.2) — detect by magic bytes, not file extension (FR-04.1).
- **`app`**: Compose UI shell, share-sheet integration, owns no transport/security logic itself — consumes state from the other modules (BRD 06).

## Known open issue (flagged in README.md, not yet resolved in the BRDs)

The top-level goal (`BRDs/00-overview.md`) targets tap→first-byte < 1.5s for known devices, but `01-discovery-module.md` (NFR-01.2) allows discovery alone up to 2s and `02-transport-negotiation-module.md` (NFR-02.1) allows up to 1s more for negotiation — the sub-budgets already exceed the top-level target. Don't treat these NFRs as consistent acceptance gates until reconciled.
