package com.swiftshare.core.discovery

import com.swiftshare.core.security.RotatingDeviceId
import kotlinx.coroutines.flow.Flow

/** BLE advertising of this device's presence + capability payload (BRD Module 01, FR-01.1/FR-01.3). */
interface DeviceAdvertiser {
    fun startAdvertising(capabilities: DeviceCapabilities)
    fun stopAdvertising()
}

/** BLE scanning for nearby app instances (BRD Module 01, FR-01.2/FR-01.7). */
interface DeviceScanner {
    fun scan(): Flow<DiscoveredDevice>
    fun stopScan()
}

data class DeviceCapabilities(
    val appVersion: String,
    val supportsWifiAware: Boolean,
    val supportsWifiDirect: Boolean,
    val supportsUwb: Boolean,
)

data class DiscoveredDevice(
    val id: RotatingDeviceId,
    val capabilities: DeviceCapabilities,
    val rssi: Int,
    /** Null unless both ends support UWB ranging (FR-01.4). */
    val rangingInfo: RangingInfo?,
)

data class RangingInfo(
    val distanceMeters: Float,
    val angleDegrees: Float,
)
