package com.swiftshare.core.security

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exercises the crypto math (ECDH agreement, AES-GCM round trip) without an
 * Android Context/Keystore — the parts that would silently break a transfer
 * if the math were wrong. [EcdhPairingManager]/[EncryptedPrefsKeyStore]
 * themselves need an instrumented/Robolectric test to cover the Context path.
 */
class CryptoRoundTripTest {

    @Test
    fun `two independently generated EC keypairs derive the same ECDH shared secret`() {
        val alice = generateEcKeyPair()
        val bob = generateEcKeyPair()

        val aliceSecret = agree(alice.private, decode(bob.public.encoded))
        val bobSecret = agree(bob.private, decode(alice.public.encoded))

        assertArrayEquals(aliceSecret, bobSecret)
    }

    @Test
    fun `AES-GCM decrypts what it encrypted`() {
        val keyStore = InMemoryKeyStore().apply { storeSessionKey(HASH, KEY) }
        val crypto = AesGcmSessionCrypto(keyStore)
        val plaintext = "hello swiftshare".toByteArray()

        val ciphertext = crypto.encrypt(PEER_ID, plaintext)
        val decrypted = crypto.decrypt(PEER_ID, ciphertext)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test(expected = IllegalStateException::class)
    fun `encrypt fails without a paired session key`() {
        AesGcmSessionCrypto(InMemoryKeyStore()).encrypt(PEER_ID, "x".toByteArray())
    }

    @Test
    fun `peer id hash is stable across calls`() {
        assertEquals(hashOf(PEER_ID), hashOf(PEER_ID))
    }

    private fun generateEcKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun decode(encoded: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))

    private fun agree(private: PrivateKey, public: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").apply {
            init(private)
            doPhase(public, true)
        }.generateSecret()

    private class InMemoryKeyStore : KeyStore {
        private val keys = mutableMapOf<String, ByteArray>()
        override fun storeSessionKey(peerIdHash: String, key: ByteArray) { keys[peerIdHash] = key }
        override fun sessionKeyFor(peerIdHash: String): ByteArray? = keys[peerIdHash]
        override fun removeSessionKey(peerIdHash: String) { keys.remove(peerIdHash) }
    }

    companion object {
        private val PEER_ID = RotatingDeviceId("test-peer-id")
        private val HASH = hashOf(PEER_ID)
        private val KEY = ByteArray(32) { it.toByte() }
    }
}
