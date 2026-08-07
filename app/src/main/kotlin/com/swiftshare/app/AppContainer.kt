package com.swiftshare.app

import android.content.Context
import com.swiftshare.core.discovery.BleDeviceScanner
import com.swiftshare.core.discovery.DeviceScanner
import com.swiftshare.core.discovery.DiscoveredDevice
import com.swiftshare.core.security.EcdhPairingManager
import com.swiftshare.core.security.EncryptedPrefsKeyStore
import com.swiftshare.core.security.PairingManager
import com.swiftshare.core.transport.DefaultTransportManager
import com.swiftshare.core.transport.TransportManager
import com.swiftshare.core.transport.WifiAwareTransport
import com.swiftshare.core.transport.WifiDirectTransport

/**
 * Manual wiring, no DI framework — five leaf singletons don't need Hilt.
 *
 * ponytail: [peerMacAddressResolver] stub mirrors [WifiDirectTransport]'s own
 * documented gap — [DiscoveredDevice] carries a BLE-scoped RotatingDeviceId,
 * not a Wi-Fi MAC. Wire real resolution once FR-02.1's capability manifest
 * carries per-transport addressing.
 */
class AppContainer(context: Context) {
    val deviceScanner: DeviceScanner = BleDeviceScanner(context)
    val pairingManager: PairingManager = EcdhPairingManager(EncryptedPrefsKeyStore(context))
    val transportManager: TransportManager = DefaultTransportManager(
        listOf(
            WifiAwareTransport(context),
            WifiDirectTransport(context, peerMacAddressResolver = {
                error("Wi-Fi Direct MAC resolution not wired yet — no per-transport addressing in DiscoveredDevice")
            }),
        ),
    )
}
