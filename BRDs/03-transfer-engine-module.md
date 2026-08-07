# BRD Module 03 — Transfer Engine

**Parent Document:** 00-overview.md
**Module Owner:** TBD
**Status:** Draft v1.0

---

## 1. Purpose

Move file bytes reliably and quickly across one or more established transport handles (from Module 02), with chunk-level integrity checking and resume, so large-file transfers survive drops and use available bandwidth efficiently.

## 2. Scope

- File chunking strategy.
- Single-transport and multi-transport (bonded) streaming.
- Per-chunk checksum verification.
- Resume-from-failure logic.
- Progress reporting to UI.

## 3. Functional Requirements

| ID | Requirement | Priority |
|---|---|---|
| FR-03.1 | App shall split files into fixed-size chunks (default 4MB, configurable) before transfer. | Must |
| FR-03.2 | Each chunk shall be tagged with a sequence number and a SHA-256 checksum, verified on receipt before acknowledgment. | Must |
| FR-03.3 | If a chunk fails checksum verification or transfer, only that chunk shall be re-requested/retransmitted — not the entire file. | Must |
| FR-03.4 | When multiple transport handles are active (per Module 02, FR-02.3), the engine shall distribute chunks across available transports using a round-robin or load-adaptive scheduler based on observed per-transport throughput. | Should |
| FR-03.5 | If a transfer is interrupted (app closed, connection dropped) and later resumed within a defined session window (default: 10 minutes), the engine shall resume from the last successfully-acknowledged chunk rather than restarting. | Must |
| FR-03.6 | Engine shall report real-time progress (bytes transferred, current speed, ETA) to the UI layer at least once per second. | Must |
| FR-03.7 | Engine shall support queuing multiple files/a folder as a single logical transfer job. | Should |
| FR-03.8 | On full transfer completion, receiver shall verify a whole-file checksum (in addition to per-chunk checksums) before marking the file as successfully received. | Must |

## 4. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-03.1 | Chunk size shall be tunable without requiring an app update (remote config or local settings), to allow tuning for different radio conditions. |
| NFR-03.2 | Multi-transport bonding shall degrade gracefully to single-transport if a secondary transport drops mid-transfer, without failing the overall job. |
| NFR-03.3 | Memory usage shall not scale with file size — engine shall stream chunks to/from disk rather than holding the full file in memory. |
| NFR-03.4 | Transfer engine shall achieve throughput within 90% of the theoretical max of the underlying transport(s) under good signal conditions. |

## 5. User Stories

- **US-03.1:** As a user, if my transfer gets interrupted at 90%, I don't want to start over from zero.
- **US-03.2:** As a user sending a large video, I want to see accurate progress and time remaining.
- **US-03.3:** As a user, I want to select multiple photos/a folder and have them sent as one job, not one-by-one.

## 6. Acceptance Criteria

- Given a transfer interrupted at any point past 10% completion, resuming within the session window shall not re-transfer more than one chunk's worth of already-received data.
- Given two active transport handles, observed aggregate throughput shall exceed single-transport throughput by a measurable margin under lab conditions (both radios unsaturated).
- Given a corrupted chunk (simulated), the engine shall detect and re-request only that chunk, verified via logs/telemetry.

## 7. Dependencies

- Depends on Module 02 for one or more ready transport handles.
- Feeds Module 04 (compression happens before chunking) and Module 05 (chunks are encrypted before transmission).
- Reports state to Module 06 (UI).

## 8. Out of Scope

- Cloud-based resumable uploads (this is a P2P-only engine; no server-side chunk storage).
- Cross-device transfer scheduling (e.g., sending the same file to 5 people at once) — candidate for v2 "group share" scope.
