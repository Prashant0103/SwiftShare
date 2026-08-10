package com.swiftshare.core.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * BLE scanning (BRD Module 01, FR-01.2). Stops itself after 60s with no new
 * result (FR-01.7) — the watchdog resets on every scan result and closes the
 * flow if it ever fires.
 *
 * @SuppressLint("MissingPermission"): see [BleDeviceAdvertiser]'s note — same
 * try/catch-based handling via [requiringBluetoothPermission].
 */
@SuppressLint("MissingPermission")
class BleDeviceScanner(context: Context) : DeviceScanner {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var activeCallback: ScanCallback? = null

    override fun scan(): Flow<DiscoveredDevice> = callbackFlow {
        val scanner = checkNotNull(adapter?.bluetoothLeScanner) {
            "BLE scanning unavailable — no adapter or hardware doesn't support it"
        }

        var watchdog: Job? = null
        fun resetWatchdog() {
            watchdog?.cancel()
            watchdog = launch {
                delay(INACTIVITY_TIMEOUT.inWholeMilliseconds)
                close()
            }
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val serviceData = result.scanRecord?.getServiceData(ParcelUuid(AdvertisementCodec.SERVICE_UUID)) ?: return
                val decoded = AdvertisementCodec.decode(serviceData) ?: return
                trySend(decoded.copy(rssi = result.rssi))
                resetWatchdog()
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed with error code $errorCode"))
            }
        }
        activeCallback = callback

        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(AdvertisementCodec.SERVICE_UUID)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setLegacy(false) // advertiser sends BLE5 extended sets to fit the pairing public key
            .build()
        requiringBluetoothPermission("Starting BLE scan") {
            scanner.startScan(listOf(filter), settings, callback)
        }
        resetWatchdog()

        awaitClose {
            watchdog?.cancel()
            requiringBluetoothPermission("Stopping BLE scan") { scanner.stopScan(callback) }
            activeCallback = null
        }
    }

    override fun stopScan() {
        val callback = activeCallback ?: return
        requiringBluetoothPermission("Stopping BLE scan") {
            adapter?.bluetoothLeScanner?.stopScan(callback)
        }
        activeCallback = null
    }

    private companion object {
        val INACTIVITY_TIMEOUT = 60.seconds
    }
}
