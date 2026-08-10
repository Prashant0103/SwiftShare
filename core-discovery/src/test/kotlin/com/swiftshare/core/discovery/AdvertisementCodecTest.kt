package com.swiftshare.core.discovery

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdvertisementCodecTest {

    @Test
    fun `decode recovers the capabilities encode was given`() {
        val capabilities = DeviceCapabilities(
            appVersion = "irrelevant-on-the-wire",
            supportsWifiAware = true,
            supportsWifiDirect = false,
            supportsUwb = true,
        )
        val sessionId = ByteArray(AdvertisementCodec.SESSION_ID_LENGTH_BYTES) { it.toByte() }
        val publicKey = ByteArray(91) { it.toByte() }

        val decoded = AdvertisementCodec.decode(AdvertisementCodec.encode(capabilities, sessionId, publicKey))

        assertEquals(true, decoded?.capabilities?.supportsWifiAware)
        assertEquals(false, decoded?.capabilities?.supportsWifiDirect)
        assertEquals(true, decoded?.capabilities?.supportsUwb)
        assertEquals(sessionId.joinToString("") { "%02x".format(it) }, decoded?.id?.value)
        assertArrayEquals(publicKey, decoded?.publicKey)
    }

    @Test
    fun `encode rejects a session id of the wrong length`() {
        val capabilities = DeviceCapabilities("v", supportsWifiAware = false, supportsWifiDirect = false, supportsUwb = false)
        try {
            AdvertisementCodec.encode(capabilities, ByteArray(4), ByteArray(91))
            error("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `decode returns null for a too-short payload`() {
        assertNull(AdvertisementCodec.decode(byteArrayOf(1, 2, 3)))
    }
}
