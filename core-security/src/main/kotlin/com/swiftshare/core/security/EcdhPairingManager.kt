package com.swiftshare.core.security

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pairing via ECDH (secp256r1) over an out-of-band-exchanged public key (BRD
 * Module 05, FR-05.1/FR-05.2). The identity keypair is generated once and its
 * private key persisted only inside [EncryptedPrefsKeyStore]'s encrypted file
 * — it never leaves the device (NFR-05.2).
 *
 * ponytail: shared-secret -> AES key is a single SHA-256, not HKDF. Good
 * enough for a single fixed-purpose derivation; switch to HKDF-SHA256 if a
 * second key ever needs deriving from the same secret.
 */
class EcdhPairingManager(private val keyStore: EncryptedPrefsKeyStore) : PairingManager {

    private val identityKeyPair by lazy { loadOrCreateIdentityKeyPair() }

    /** peerId -> derived AES-256 session key, awaiting user confirmation of the displayed code. */
    private val pendingSessionKeys = mutableMapOf<RotatingDeviceId, ByteArray>()

    override fun localPublicKey(): ByteArray = identityKeyPair.public.encoded

    override suspend fun beginPairing(peerId: RotatingDeviceId, peerPublicKey: ByteArray): PairingChallenge =
        withContext(Dispatchers.Default) {
            val sharedSecret = deriveSharedSecret(decodePublicKey(peerPublicKey))
            pendingSessionKeys[peerId] = sha256(sharedSecret + "swiftshare-session-key".toByteArray())
            PairingChallenge(peerId, displayCodeFor(sharedSecret))
        }

    override suspend fun confirmPairing(challenge: PairingChallenge): PairingResult {
        val sessionKey = pendingSessionKeys.remove(challenge.peerId)
            ?: return PairingResult.Rejected("No pairing in progress for this device")

        val peerIdHash = hashOf(challenge.peerId)
        keyStore.storeSessionKey(peerIdHash, sessionKey)
        val trustedDevice = TrustedDevice(peerIdHash, System.currentTimeMillis())
        TrustedDeviceList.add(keyStore, trustedDevice)
        return PairingResult.Success(trustedDevice)
    }

    override suspend fun isPaired(peerId: RotatingDeviceId): Boolean =
        keyStore.sessionKeyFor(hashOf(peerId)) != null

    override suspend fun revoke(peerId: RotatingDeviceId) {
        val peerIdHash = hashOf(peerId)
        keyStore.removeSessionKey(peerIdHash)
        TrustedDeviceList.remove(keyStore, peerIdHash)
    }

    override suspend fun pairedDevices(): List<TrustedDevice> = TrustedDeviceList.readAll(keyStore)

    private fun deriveSharedSecret(peerPublicKey: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").apply {
            init(identityKeyPair.private)
            doPhase(peerPublicKey, true)
        }.generateSecret()

    private fun displayCodeFor(sharedSecret: ByteArray): String {
        val digest = sha256(sharedSecret + "swiftshare-display-code".toByteArray())
        val sixDigits = ((digest[0].toInt() and 0xFF) shl 16 or
            (digest[1].toInt() and 0xFF shl 8) or
            (digest[2].toInt() and 0xFF)) % 1_000_000
        return sixDigits.toString().padStart(6, '0')
    }

    private fun loadOrCreateIdentityKeyPair(): java.security.KeyPair {
        val storedPrivate = keyStore.getString(IDENTITY_PRIVATE_KEY_PREF)
        val storedPublic = keyStore.getString(IDENTITY_PUBLIC_KEY_PREF)
        if (storedPrivate != null && storedPublic != null) {
            val factory = KeyFactory.getInstance("EC")
            val private = factory.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(storedPrivate, Base64.NO_WRAP)))
            val public = factory.generatePublic(X509EncodedKeySpec(Base64.decode(storedPublic, Base64.NO_WRAP)))
            return java.security.KeyPair(public, private)
        }

        val keyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

        keyStore.putString(IDENTITY_PRIVATE_KEY_PREF, Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP))
        keyStore.putString(IDENTITY_PUBLIC_KEY_PREF, Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP))
        return keyPair
    }

    companion object {
        private const val IDENTITY_PRIVATE_KEY_PREF = "identity_private_key"
        private const val IDENTITY_PUBLIC_KEY_PREF = "identity_public_key"

        private fun decodePublicKey(encoded: ByteArray): PublicKey =
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))

        private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}

/** Flat "hash|epochMillis" line list — avoids pulling in a JSON dependency for one small record type. */
private object TrustedDeviceList {
    private const val PREF_KEY = "trusted_devices"

    fun readAll(keyStore: EncryptedPrefsKeyStore): List<TrustedDevice> =
        keyStore.getString(PREF_KEY)
            ?.split(";")
            ?.filter { it.isNotBlank() }
            ?.map { line -> line.split("|").let { TrustedDevice(it[0], it[1].toLong()) } }
            ?: emptyList()

    fun add(keyStore: EncryptedPrefsKeyStore, device: TrustedDevice) {
        val remaining = readAll(keyStore).filterNot { it.peerIdHash == device.peerIdHash }
        writeAll(keyStore, remaining + device)
    }

    fun remove(keyStore: EncryptedPrefsKeyStore, peerIdHash: String) {
        writeAll(keyStore, readAll(keyStore).filterNot { it.peerIdHash == peerIdHash })
    }

    private fun writeAll(keyStore: EncryptedPrefsKeyStore, devices: List<TrustedDevice>) {
        keyStore.putString(PREF_KEY, devices.joinToString(";") { "${it.peerIdHash}|${it.pairedAtEpochMillis}" })
    }
}
