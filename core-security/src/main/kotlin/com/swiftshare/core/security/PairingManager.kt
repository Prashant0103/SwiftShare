package com.swiftshare.core.security

/**
 * Owns first-time device pairing (BRD Module 05, FR-05.1/FR-05.2): mutual
 * confirmation of a short code, followed by ECDH-derived session key storage.
 */
interface PairingManager {

    /** X.509-encoded EC public key to hand to the peer over BLE (Module 01/02's job to transmit it). */
    fun localPublicKey(): ByteArray

    /** [peerPublicKey] is the peer's X.509-encoded EC public key, already exchanged over BLE. */
    suspend fun beginPairing(peerId: RotatingDeviceId, peerPublicKey: ByteArray): PairingChallenge

    suspend fun confirmPairing(challenge: PairingChallenge): PairingResult

    suspend fun isPaired(peerId: RotatingDeviceId): Boolean

    suspend fun revoke(peerId: RotatingDeviceId)

    suspend fun pairedDevices(): List<TrustedDevice>
}

/** Rotates per session (FR-01.8) — never a persistent hardware identifier (FR-05.4). */
@JvmInline
value class RotatingDeviceId(val value: String)

data class PairingChallenge(
    val peerId: RotatingDeviceId,
    val displayCode: String,
)

sealed interface PairingResult {
    data class Success(val trustedDevice: TrustedDevice) : PairingResult
    data class Rejected(val reason: String) : PairingResult
}

data class TrustedDevice(
    val peerIdHash: String,
    val pairedAtEpochMillis: Long,
)
