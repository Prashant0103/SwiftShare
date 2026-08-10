package com.swiftshare.core.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.security.SecureRandom

/**
 * BLE advertising (BRD Module 01, FR-01.1/FR-01.3). The 8-byte session id is
 * regenerated every time [startAdvertising] runs — NFR-01.4 requires it to
 * rotate per session, so it's never persisted.
 *
 * Uses BLE5 extended advertising (non-legacy), not the ~31-byte legacy
 * format — [AdvertisementCodec] now carries an X.509 EC public key
 * (~91 bytes) for first-time pairing (FR-05.1), which doesn't fit legacy.
 * Requires BLE5 hardware; ponytail: no legacy fallback if the chipset lacks it.
 *
 * @SuppressLint("MissingPermission"): permission is genuinely handled, just
 * inside [requiringBluetoothPermission]'s try/catch, which lint's analysis
 * doesn't see through. Requesting BLUETOOTH_ADVERTISE/SCAN is Module 06's job.
 */
@SuppressLint("MissingPermission")
class BleDeviceAdvertiser(context: Context) : DeviceAdvertiser {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var activeCallback: AdvertisingSetCallback? = null

    override fun startAdvertising(capabilities: DeviceCapabilities, localPublicKey: ByteArray) {
        val advertiser = checkNotNull(adapter?.bluetoothLeAdvertiser) {
            "BLE advertising unavailable — no adapter or hardware doesn't support it"
        }
        // Fail loudly here rather than through startAdvertisingSet: the payload carries an X.509 EC
        // public key that only fits a BLE5 extended set, so on a chipset without it advertising
        // never starts and the peer's scan just silently finds nothing.
        check(adapter?.isLeExtendedAdvertisingSupported == true) {
            "BLE5 extended advertising unsupported on this chipset — advertisement payload won't fit"
        }
        stopAdvertising()

        val sessionId = ByteArray(AdvertisementCodec.SESSION_ID_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(false)
            .setConnectable(false)
            .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(AdvertisementCodec.SERVICE_UUID))
            .addServiceData(
                ParcelUuid(AdvertisementCodec.SERVICE_UUID),
                AdvertisementCodec.encode(capabilities, sessionId, localPublicKey),
            )
            .build()

        // Was `object : AdvertisingSetCallback() {}` — an empty callback swallowed the start status,
        // so a failed advertise looked identical to a working one and the only symptom was the peer
        // finding no devices. ponytail: logs only; plumb a state Flow out if the UI needs to react.
        val callback = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(set: AdvertisingSet?, txPower: Int, status: Int) {
                if (status != ADVERTISE_SUCCESS) {
                    Log.e(TAG, "BLE advertising failed to start, status=$status — peers won't see this device")
                }
            }
        }
        activeCallback = callback
        requiringBluetoothPermission("Starting BLE advertising") {
            advertiser.startAdvertisingSet(parameters, data, null, null, null, callback)
        }
    }

    override fun stopAdvertising() {
        val callback = activeCallback ?: return
        requiringBluetoothPermission("Stopping BLE advertising") {
            adapter?.bluetoothLeAdvertiser?.stopAdvertisingSet(callback)
        }
        activeCallback = null
    }

    private companion object {
        const val TAG = "BleDeviceAdvertiser"
    }
}
