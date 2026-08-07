package com.swiftshare.core.security

/**
 * On-device-only storage for pairing/session key material (BRD Module 05,
 * NFR-05.2 — no trust or pairing data ever leaves the device).
 */
interface KeyStore {

    fun storeSessionKey(peerIdHash: String, key: ByteArray)

    fun sessionKeyFor(peerIdHash: String): ByteArray?

    fun removeSessionKey(peerIdHash: String)
}
