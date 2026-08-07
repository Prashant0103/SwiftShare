package com.swiftshare.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM over the paired session key (BRD Module 05, FR-05.3). */
class AesGcmSessionCrypto(private val keyStore: KeyStore) : SessionCrypto {

    override fun encrypt(peerId: RotatingDeviceId, plaintext: ByteArray): ByteArray {
        val key = sessionKeyOf(peerId)
        val iv = ByteArray(IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        return iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(peerId: RotatingDeviceId, ciphertext: ByteArray): ByteArray {
        require(ciphertext.size > IV_LENGTH_BYTES) { "Ciphertext too short to contain an IV" }
        val key = sessionKeyOf(peerId)
        val iv = ciphertext.copyOfRange(0, IV_LENGTH_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        return cipher.doFinal(ciphertext, IV_LENGTH_BYTES, ciphertext.size - IV_LENGTH_BYTES)
    }

    private fun sessionKeyOf(peerId: RotatingDeviceId): SecretKeySpec {
        val raw = checkNotNull(keyStore.sessionKeyFor(hashOf(peerId))) {
            "No session key for peer $peerId — pair before sending"
        }
        return SecretKeySpec(raw, "AES")
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
