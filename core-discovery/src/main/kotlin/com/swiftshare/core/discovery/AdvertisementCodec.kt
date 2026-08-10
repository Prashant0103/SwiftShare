package com.swiftshare.core.discovery

import com.swiftshare.core.security.RotatingDeviceId
import java.util.UUID

/**
 * BLE legacy advertising packets cap out around 31 bytes total, too little to
 * also carry a public key — so this rides on BLE5 extended advertising
 * instead (BRD Module 01, FR-01.3 for the capability/session fields; FR-05.1
 * for the public key, added so a peer can pair on first contact without a
 * separate exchange channel). Wire format: capability bitmask, a numeric
 * protocol version (not the full [DeviceCapabilities.appVersion] string —
 * that doesn't fit), an 8-byte rotating session id, then a 1-byte length
 * prefix + the X.509-encoded EC public key.
 */
internal object AdvertisementCodec {
    const val PROTOCOL_VERSION: Byte = 1

    /** Identifies SwiftShare's BLE advertisements among all nearby BLE traffic. */
    val SERVICE_UUID: UUID = UUID.fromString("6f9a1e2c-6b6d-4a6a-9b1e-000000000001")

    const val SESSION_ID_LENGTH_BYTES = 8
    private const val FLAG_WIFI_AWARE = 1 shl 0
    private const val FLAG_WIFI_DIRECT = 1 shl 1
    private const val FLAG_UWB = 1 shl 2
    private const val HEADER_LENGTH_BYTES = 2 + SESSION_ID_LENGTH_BYTES + 1 // flags + version + sessionId + keyLen

    fun encode(capabilities: DeviceCapabilities, sessionId: ByteArray, publicKey: ByteArray): ByteArray {
        require(sessionId.size == SESSION_ID_LENGTH_BYTES) { "Session id must be $SESSION_ID_LENGTH_BYTES bytes" }
        require(publicKey.size <= 255) { "Public key must fit in a 1-byte length prefix" }
        var flags = 0
        if (capabilities.supportsWifiAware) flags = flags or FLAG_WIFI_AWARE
        if (capabilities.supportsWifiDirect) flags = flags or FLAG_WIFI_DIRECT
        if (capabilities.supportsUwb) flags = flags or FLAG_UWB
        return byteArrayOf(flags.toByte(), PROTOCOL_VERSION) + sessionId + byteArrayOf(publicKey.size.toByte()) + publicKey
    }

    /** Returns null for malformed/foreign packets rather than throwing — scan results are untrusted input. */
    fun decode(bytes: ByteArray): DiscoveredDevice? {
        if (bytes.size < HEADER_LENGTH_BYTES) return null
        val flags = bytes[0].toInt()
        val sessionId = bytes.copyOfRange(2, 2 + SESSION_ID_LENGTH_BYTES)
        val keyLength = bytes[HEADER_LENGTH_BYTES - 1].toInt() and 0xFF
        if (bytes.size < HEADER_LENGTH_BYTES + keyLength) return null
        val publicKey = bytes.copyOfRange(HEADER_LENGTH_BYTES, HEADER_LENGTH_BYTES + keyLength)
        val capabilities = DeviceCapabilities(
            appVersion = "protocol-v${bytes[1]}",
            supportsWifiAware = flags and FLAG_WIFI_AWARE != 0,
            supportsWifiDirect = flags and FLAG_WIFI_DIRECT != 0,
            supportsUwb = flags and FLAG_UWB != 0,
        )
        val id = RotatingDeviceId(sessionId.joinToString("") { "%02x".format(it) })
        return DiscoveredDevice(id = id, capabilities = capabilities, rssi = 0, rangingInfo = null, publicKey = publicKey)
    }
}
