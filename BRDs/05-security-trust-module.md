# BRD Module 05 — Security & Trust

**Parent Document:** 00-overview.md
**Module Owner:** TBD
**Status:** Draft v1.0

---

## 1. Purpose

Ensure files are only sent to intended recipients, transferred data is encrypted in transit, and no persistent identifying information is leaked over the air — without requiring cloud accounts or central servers.

## 2. Scope

- Device pairing and trust model.
- Encryption of data in transit.
- Permission/consent flows.
- Local storage of trust data (no cloud).

## 3. Functional Requirements

| ID | Requirement | Priority |
|---|---|---|
| FR-05.1 | First-time connection between two devices shall require explicit mutual confirmation (e.g., matching a short numeric/emoji code shown on both screens) before any file transfer is permitted. | Must |
| FR-05.2 | Once paired, a shared symmetric key shall be derived (e.g., via ECDH key exchange) and stored locally on both devices, hashed against the peer's rotating ID, to skip re-pairing confirmation on future transfers. | Must |
| FR-05.3 | All chunk data (post-compression, per Module 04) shall be encrypted using the paired session key (e.g., AES-256-GCM) before transmission over any transport. | Must |
| FR-05.4 | App shall never transmit a persistent hardware identifier (IMEI, MAC address) in discovery or negotiation payloads — only rotating session IDs (per Module 01, FR-01.8). | Must |
| FR-05.5 | User shall be able to view and revoke previously-paired devices from a settings screen. | Must |
| FR-05.6 | App shall request only the minimum Android permissions required: Nearby Wi-Fi Devices, Bluetooth, Storage/Media access — and explain each at the point of request. | Must |
| FR-05.7 | Incoming file transfer requests from unpaired devices shall always require explicit accept/reject from the user — no silent auto-accept ever. | Must |

## 4. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-05.1 | Encryption/decryption shall not reduce transfer throughput by more than ~5% compared to unencrypted transfer (hardware-accelerated AES where available). |
| NFR-05.2 | No trust or pairing data shall ever leave the device (no cloud sync of the pairing list) in v1. |
| NFR-05.3 | Pairing confirmation UI (numeric/emoji code match) shall take no more than 2 user taps to complete. |

## 5. User Stories

- **US-05.1:** As a user, I want confidence that a stranger's phone can't silently pull files off mine.
- **US-05.2:** As a privacy-conscious user, I want to know the app isn't leaking my phone's permanent ID to nearby devices.
- **US-05.3:** As a user, I want to remove old paired devices I no longer share with.

## 6. Acceptance Criteria

- Given two devices pairing for the first time, transfer is blocked until both users confirm a matching code.
- Given a paired device reconnecting, no code confirmation is required, but encryption is still applied to every chunk.
- Given a device revoked from the trust list, that device must re-pair (repeat FR-05.1) before any future transfer is allowed.
- Packet capture of discovery/negotiation traffic (for QA) shows no MAC address or IMEI in cleartext.

## 7. Dependencies

- Module 01 (rotating device IDs) and Module 02 (capability exchange channel) both carry security-relevant payloads governed by this module.
- Module 03 encrypts chunks using keys established here before transmission.

## 8. Out of Scope

- Cloud-based identity/account systems.
- End-to-end audit logging or enterprise device management (MDM) integration — could be a future enterprise-tier feature, not v1 consumer scope.
