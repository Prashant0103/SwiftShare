package com.swiftshare.core.transport

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import com.swiftshare.core.discovery.DiscoveredDevice
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val PORT = 5432
private const val SOCKET_CONNECT_TIMEOUT_MS = 10_000
private const val TAG = "WifiDirectTransport"

/**
 * Wi-Fi Direct transport (BRD Module 02, FR-02.2 tier 2 fallback). Both halves
 * of the link live here now:
 *
 *  - [listen] makes this device an autonomous group owner and accepts one
 *    inbound socket on [PORT] — the receiving side.
 *  - [connect] discovers the group owner, joins its group, and opens a socket
 *    to it — the sending side.
 *
 * Group formation is driven off `WIFI_P2P_CONNECTION_CHANGED_ACTION` rather
 * than [WifiP2pManager.ActionListener.onSuccess]: onSuccess only means the
 * request was accepted by the framework, not that the group exists, so reading
 * connection info there returns a null groupOwnerAddress.
 */
@SuppressLint("MissingPermission")
class WifiDirectTransport(private val context: Context) : Transport {
    override val kind = TransportKind.WIFI_DIRECT

    private val manager: WifiP2pManager?
        get() = context.getSystemService(WifiP2pManager::class.java)

    override suspend fun connect(peer: DiscoveredDevice): TransportHandle {
        val manager = checkNotNull(manager) { "Wi-Fi Direct unavailable on this device" }
        val channel = manager.initialize(context, context.mainLooper, null)

        val owner = discoverGroupOwner(manager, channel)
        connectToGroup(manager, channel, owner.deviceAddress)
        val info = awaitConnectionInfo(manager, channel)
        val ownerAddress = checkNotNull(info.groupOwnerAddress) { "Wi-Fi Direct group formed without an owner address" }

        val socket = withContext(Dispatchers.IO) {
            Socket().also { it.connect(InetSocketAddress(ownerAddress, PORT), SOCKET_CONNECT_TIMEOUT_MS) }
        }
        return SocketTransportHandle(kind, socket) { manager.removeGroup(channel, null) }
    }

    /**
     * Receiving side: become an autonomous group owner and block until one peer
     * connects. Suspends until the inbound socket arrives, so the caller decides
     * how long to wait.
     */
    suspend fun listen(): TransportHandle {
        val manager = checkNotNull(manager) { "Wi-Fi Direct unavailable on this device" }
        val channel = manager.initialize(context, context.mainLooper, null)

        // Any group left over from a previous run keeps createGroup from succeeding.
        runCatching { removeGroup(manager, channel) }
        createGroup(manager, channel)

        val serverSocket = withContext(Dispatchers.IO) { ServerSocket(PORT) }
        val socket = withContext(Dispatchers.IO) { serverSocket.accept() }
        return SocketTransportHandle(kind, socket) {
            runCatching { serverSocket.close() }
            manager.removeGroup(channel, null)
        }
    }

    /**
     * ponytail: picks the group owner out of the P2P peer list, falling back to
     * the sole peer — [peer]'s BLE-scoped RotatingDeviceId can't be matched to a
     * Wi-Fi MAC, and putting a MAC in the BLE advert would break FR-05.4. Fine
     * with one receiver in range; needs FR-02.1 per-transport addressing to pick
     * correctly among several.
     */
    private suspend fun discoverGroupOwner(manager: WifiP2pManager, channel: WifiP2pManager.Channel): WifiP2pDevice {
        discoverPeers(manager, channel)
        val peers = awaitPeers(manager, channel)
        check(peers.isNotEmpty()) { "no Wi-Fi Direct peers found — is the other phone on the Receive screen?" }
        return peers.firstOrNull { it.isGroupOwner } ?: peers.first()
    }

    private suspend fun discoverPeers(manager: WifiP2pManager, channel: WifiP2pManager.Channel) =
        suspendCancellableCoroutine<Unit> { cont ->
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = cont.resume(Unit)
                override fun onFailure(reason: Int) =
                    cont.resumeWithException(IllegalStateException("Wi-Fi Direct peer discovery failed: reason=$reason"))
            })
        }

    /** Waits for a non-empty peer list via WIFI_P2P_PEERS_CHANGED_ACTION. */
    private suspend fun awaitPeers(manager: WifiP2pManager, channel: WifiP2pManager.Channel): List<WifiP2pDevice> =
        awaitBroadcast(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION) { cont ->
            manager.requestPeers(channel) { list: WifiP2pDeviceList ->
                val peers = list.deviceList.toList()
                if (peers.isNotEmpty() && cont.isActive) cont.resume(peers)
            }
        }

    private suspend fun createGroup(manager: WifiP2pManager, channel: WifiP2pManager.Channel) =
        suspendCancellableCoroutine<Unit> { cont ->
            manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = cont.resume(Unit)
                override fun onFailure(reason: Int) =
                    cont.resumeWithException(IllegalStateException("Wi-Fi Direct createGroup failed: reason=$reason"))
            })
        }

    private suspend fun removeGroup(manager: WifiP2pManager, channel: WifiP2pManager.Channel) =
        suspendCancellableCoroutine<Unit> { cont ->
            manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = cont.resume(Unit)
                override fun onFailure(reason: Int) = cont.resume(Unit) // nothing to remove is the normal case
            })
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

    /** Waits for the group to actually form — see the class doc on why onSuccess isn't enough. */
    private suspend fun awaitConnectionInfo(manager: WifiP2pManager, channel: WifiP2pManager.Channel): WifiP2pInfo =
        awaitBroadcast(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) { cont ->
            manager.requestConnectionInfo(channel) { info ->
                if (info?.groupFormed == true && cont.isActive) cont.resume(info)
            }
        }

    /**
     * Registers for [action], runs [poll] on every matching broadcast plus once
     * up front (the state may already be what we want), and resumes with the
     * first value [poll] produces.
     */
    private suspend fun <T> awaitBroadcast(
        action: String,
        poll: (kotlinx.coroutines.CancellableContinuation<T>) -> Unit,
    ): T {
        var receiver: BroadcastReceiver? = null
        try {
            return suspendCancellableCoroutine { cont ->
                val created = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == action) poll(cont)
                    }
                }
                receiver = created
                context.registerReceiver(created, IntentFilter(action))
                poll(cont)
            }
        } finally {
            // finally, not invokeOnCancellation: this has to run on normal resume too, and
            // negotiate()'s withTimeout cancels us mid-wait on the failure path.
            receiver?.let {
                runCatching { context.unregisterReceiver(it) }
                    .onFailure { Log.w(TAG, "receiver already unregistered for $action") }
            }
        }
    }
}
