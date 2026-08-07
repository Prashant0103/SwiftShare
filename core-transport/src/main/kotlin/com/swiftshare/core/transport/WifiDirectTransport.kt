package com.swiftshare.core.transport

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import com.swiftshare.core.discovery.DiscoveredDevice
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val PORT = 5432
private const val SOCKET_CONNECT_TIMEOUT_MS = 5_000

/**
 * Wi-Fi Direct transport (BRD Module 02, FR-02.2 tier 2 fallback). Forms a
 * P2P group with the peer, then opens a plain socket to the group owner.
 *
 * ponytail: only implements the client side (connect-then-socket-to-owner).
 * Whichever device becomes group owner also needs a `ServerSocket` accepting
 * on [PORT] — that accept loop belongs to whoever owns the transfer-receiving
 * side in core-transfer, not this negotiation-layer class. Add there.
 *
 * ponytail: [peerMacAddressResolver] is a stopgap — [DiscoveredDevice] carries
 * a BLE-scoped [com.swiftshare.core.security.RotatingDeviceId], not a Wi-Fi
 * MAC. Wire real resolution once FR-02.1's capability manifest is extended to
 * carry per-transport addressing.
 */
@SuppressLint("MissingPermission")
class WifiDirectTransport(
    private val context: Context,
    private val peerMacAddressResolver: (DiscoveredDevice) -> String,
) : Transport {
    override val kind = TransportKind.WIFI_DIRECT

    override suspend fun connect(peer: DiscoveredDevice): TransportHandle {
        val manager = checkNotNull(context.getSystemService(WifiP2pManager::class.java)) {
            "Wi-Fi Direct unavailable on this device"
        }
        val channel = manager.initialize(context, context.mainLooper, null)

        connectToGroup(manager, channel, peerMacAddressResolver(peer))
        val info = awaitConnectionInfo(manager, channel)
        val ownerAddress = checkNotNull(info.groupOwnerAddress) { "Wi-Fi Direct group formed without an owner address" }

        val socket = Socket()
        socket.connect(InetSocketAddress(ownerAddress, PORT), SOCKET_CONNECT_TIMEOUT_MS)
        return SocketTransportHandle(kind, socket) { manager.removeGroup(channel, null) }
    }

    private suspend fun connectToGroup(manager: WifiP2pManager, channel: WifiP2pManager.Channel, macAddress: String) =
        suspendCancellableCoroutine<Unit> { cont ->
            val config = WifiP2pConfig().apply { deviceAddress = macAddress }
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = cont.resume(Unit)
                override fun onFailure(reason: Int) =
                    cont.resumeWithException(IllegalStateException("Wi-Fi Direct connect failed: reason=$reason"))
            })
        }

    private suspend fun awaitConnectionInfo(manager: WifiP2pManager, channel: WifiP2pManager.Channel): WifiP2pInfo =
        suspendCancellableCoroutine { cont ->
            manager.requestConnectionInfo(channel) { info -> cont.resume(info) }
        }
}
