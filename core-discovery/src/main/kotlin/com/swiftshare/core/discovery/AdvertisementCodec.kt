package com.swiftshare.core.discovery

import com.swiftshare.core.security.RotatingDeviceId
import java.util.UUID

/**
 * BLE legacy advertising packets cap out around 31 bytes total, and most of
 * that is spent on the service UUID declaration — there's only room for a
 * handful of payload bytes (BRD Module 01, FR-01.3). So the wire format is
 * deliberately compact: a capability bitmask, a numeric protocol version (not
 * the full [DeviceCapabilities.appVersion] string — that doesn't fit), and an
 * 8-byte rotating session id. Wi-Fi Aware/Direct/UWB capability detection
 * happens at the caller (feature-flag lookup); this codec just serializes it.
 */
internal object AdvertisementCodec {
    const val PROTOCOL_VERSION: Byte = 1

    /** Identifies SwiftShare's BLE advertisements among all nearby BLE traffic. */
    val SERVICE_UUID: UUID = UUID.fromString("6f9a1e2c-6b6d-4a6a-9b1e-000000000001")

    const val SESSION_ID_LENGTH_BYTES = 8
    private const val FLAG_WIFI_AWARE = 1 shl 0
    private const val FLAG_WIFI_DIRECT = 1 shl 1
    private const val FLAG_UWB = 1 shl 2

    fun encode(capabilities: DeviceCapabilities, sessionId: ByteArray): ByteArray {
        require(sessionId.size == SESSION_ID_LENGTH_BYTES) { "Session id must be $SESSION_ID_LENGTH_BYTES bytes" }
        var flags = 0
        if (capabilities.supportsWifiAware) flags = flags or FLAG_WIFI_AWARE
        if (capabilities.supportsWifiDirect) flags = flags or FLAG_WIFI_DIRECT
        if (capabilities.supportsUwb) flags = flags or FLAG_UWB
        return byteArrayOf(flags.toByte(), PROTOCOL_VERSION) + sessionId
    }

    /** Returns null for malformed/foreign packets rather than throwing — scan results are untrusted input. */
    fun decode(bytes: ByteArray): DiscoveredDevice? {
        if (bytes.size < 2 + SESSION_ID_LENGTH_BYTES) return null
        val flags = bytes[0].toInt()
        val sessionId = bytes.copyOfRange(2, 2 + SESSION_ID_LENGTH_BYTES)
        val capabilities = DeviceCapabilities(
            appVersion = "protocol-v${bytes[1]}",
            supportsWifiAware = flags and FLAG_WIFI_AWARE != 0,
            supportsWifiDirect = flags and FLAG_WIFI_DIRECT != 0,
            supportsUwb = flags and FLAG_UWB != 0,
        )
        val id = RotatingDeviceId(sessionId.joinToString("") { "%02x".format(it) })
        return DiscoveredDevice(id = id, capabilities = capabilities, rssi = 0, rangingInfo = null)
    }
}
