package com.swiftshare.app

import android.content.Context
import android.os.Environment
import com.swiftshare.core.discovery.BleDeviceAdvertiser
import com.swiftshare.core.discovery.BleDeviceScanner
import com.swiftshare.core.discovery.DeviceAdvertiser
import com.swiftshare.core.discovery.DeviceCapabilities
import com.swiftshare.core.discovery.DeviceScanner
import com.swiftshare.core.discovery.DiscoveredDevice
import com.swiftshare.core.security.EcdhPairingManager
import com.swiftshare.core.security.EncryptedPrefsKeyStore
import com.swiftshare.core.security.PairingManager
import com.swiftshare.core.transfer.DefaultTransferEngine
import com.swiftshare.core.transfer.TransferEngine
import com.swiftshare.core.transport.DefaultTransportManager
import com.swiftshare.core.transport.TransportManager
import com.swiftshare.core.transport.WifiAwareTransport
import com.swiftshare.core.transport.WifiDirectTransport
import java.io.File

/** Manual wiring, no DI framework — five leaf singletons don't need Hilt. */
class AppContainer(context: Context) {
    val deviceScanner: DeviceScanner = BleDeviceScanner(context)
    val deviceAdvertiser: DeviceAdvertiser = BleDeviceAdvertiser(context)
    val localCapabilities = DeviceCapabilities(
        appVersion = "0.1.0-scaffold",
        // Advertised false until WifiAwareTransport gets a publish/listen side: it can only
        // subscribe, so as tier 1 it just burned the whole connect timeout before falling back.
        supportsWifiAware = false,
        supportsWifiDirect = true,
        supportsUwb = false,
    )
    val pairingManager: PairingManager = EcdhPairingManager(EncryptedPrefsKeyStore(context))

    /** Held concretely, not just as a [TransportManager] candidate: the receive flow needs [WifiDirectTransport.listen]. */
    val wifiDirectTransport = WifiDirectTransport(context)

    val transportManager: TransportManager = DefaultTransportManager(
        listOf(WifiAwareTransport(context), wifiDirectTransport),
    )
    /**
     * Received files land in the app's external files dir, not cacheDir: the system can evict a
     * cache directory mid-transfer, and cacheDir isn't reachable from a file manager. Visible at
     * Android/data/com.swiftshare.app/files/Download, no storage permission needed.
     */
    val receivedDirectory: File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "received")

    val transferEngine: TransferEngine = DefaultTransferEngine(outputDirectory = receivedDirectory)
}
