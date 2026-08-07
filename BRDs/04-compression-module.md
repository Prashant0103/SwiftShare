# BRD Module 04 — Compression

**Parent Document:** 00-overview.md
**Module Owner:** TBD
**Status:** Draft v1.0

---

## 1. Purpose

Reduce effective transfer size where it genuinely helps, without wasting CPU/battery compressing files that are already compressed (most photos/videos), and without degrading media quality.

## 2. Scope

- File-type detection (extension + magic-byte sniffing, not extension alone — to handle mislabeled files).
- Compression decision logic.
- Lossless compression application for compressible types only.

## 3. Functional Requirements

| ID | Requirement | Priority |
|---|---|---|
| FR-04.1 | App shall inspect each file's magic bytes (not just extension) to determine true file type before deciding whether to compress. | Must |
| FR-04.2 | App shall skip compression for already-compressed formats: JPEG, PNG (already-optimized), MP4, HEIC, MKV, ZIP, and similar. | Must |
| FR-04.3 | App shall apply lossless compression (e.g., zstd or deflate) to compressible formats: plain text, CSV, JSON, uncompressed BMP/RAW/TIFF, log files, and generic office documents (docx/xlsx/pptx use internal zip compression already — detect and skip). | Must |
| FR-04.4 | Compression shall never alter or degrade the content of the file (lossless only) — no lossy re-encoding of images/video in v1. | Must |
| FR-04.5 | Compression decision and resulting size reduction shall be logged for user-visible transparency (e.g., "Compressed 40MB → 12MB"). | Should |
| FR-04.6 | If compressing a file would take longer than the estimated time saved in transfer (based on current negotiated transport speed), skip compression. | Should |

## 4. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-04.1 | Compression/decompression shall not block the UI thread; run on a background worker. |
| NFR-04.2 | Compression decision logic shall add negligible latency (< 100ms per file) to the overall transfer start time. |
| NFR-04.3 | Compression library choice shall be open-source and free of licensing fees (e.g., zstd, which is BSD-licensed). |

## 5. User Stories

- **US-04.1:** As a user sending a folder of text-heavy documents, I want them compressed automatically to save time.
- **US-04.2:** As a user sending photos, I don't want the app wasting time trying to compress already-compressed JPEGs.

## 6. Acceptance Criteria

- Given a batch of files with mixed types, only the compressible ones show a size reduction in transfer logs.
- Given a JPEG/MP4 file, compression step is skipped entirely (verified via timing — no meaningful CPU spike for that file).
- Given a large plain-text log file, compression achieves at least the expected reduction ratio for that algorithm (benchmarked separately per library).

## 7. Dependencies

- Feeds into Module 03 (compressed output is what gets chunked and transferred).
- Independent of Module 01/02 (no transport dependency).

## 8. Out of Scope

- Lossy compression/re-encoding of media (explicitly excluded to avoid quality complaints) — could be an opt-in "reduce quality for speed" toggle in a future version, not v1.
- Server-side or cloud compression (this is fully on-device).
