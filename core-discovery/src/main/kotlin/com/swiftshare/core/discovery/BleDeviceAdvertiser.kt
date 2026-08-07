package com.swiftshare.core.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import java.security.SecureRandom

/**
 * BLE advertising (BRD Module 01, FR-01.1/FR-01.3). The 8-byte session id is
 * regenerated every time [startAdvertising] runs — NFR-01.4 requires it to
 * rotate per session, so it's never persisted.
 *
 * @SuppressLint("MissingPermission"): permission is genuinely handled, just
 * inside [requiringBluetoothPermission]'s try/catch, which lint's analysis
 * doesn't see through. Requesting BLUETOOTH_ADVERTISE/SCAN is Module 06's job.
 */
@SuppressLint("MissingPermission")
class BleDeviceAdvertiser(context: Context) : DeviceAdvertiser {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var activeCallback: AdvertiseCallback? = null

    override fun startAdvertising(capabilities: DeviceCapabilities) {
        val advertiser = checkNotNull(adapter?.bluetoothLeAdvertiser) {
            "BLE advertising unavailable — no adapter or hardware doesn't support it"
        }
        stopAdvertising()

        val sessionId = ByteArray(AdvertisementCodec.SESSION_ID_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(AdvertisementCodec.SERVICE_UUID))
            .addServiceData(ParcelUuid(AdvertisementCodec.SERVICE_UUID), AdvertisementCodec.encode(capabilities, sessionId))
            .build()

        val callback = object : AdvertiseCallback() {}
        activeCallback = callback
        requiringBluetoothPermission("Starting BLE advertising") {
            advertiser.startAdvertising(settings, data, callback)
        }
    }

    override fun stopAdvertising() {
        val callback = activeCallback ?: return
        requiringBluetoothPermission("Stopping BLE advertising") {
            adapter?.bluetoothLeAdvertiser?.stopAdvertising(callback)
        }
        activeCallback = null
    }
}
