package com.swiftshare.core.security

/**
 * Encrypts/decrypts chunk payloads using the session key derived at pairing
 * time (BRD Module 05, FR-05.3 — AES-256-GCM over the paired session key).
 */
interface SessionCrypto {

    fun encrypt(peerId: RotatingDeviceId, plaintext: ByteArray): ByteArray

    fun decrypt(peerId: RotatingDeviceId, ciphertext: ByteArray): ByteArray
}
