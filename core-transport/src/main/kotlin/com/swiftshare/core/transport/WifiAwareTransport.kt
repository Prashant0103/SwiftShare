package com.swiftshare.core.transport

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareNetworkInfo
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import com.swiftshare.core.discovery.DiscoveredDevice
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val SERVICE_NAME = "swiftshare"
private const val SOCKET_CONNECT_TIMEOUT_MS = 5_000

/**
 * Wi-Fi Aware transport (BRD Module 02, FR-02.2 tier 1 — highest priority).
 * Subscribes for the peer's publish, opens an Aware network specifier once
 * matched, then a plain socket to the peer's Aware-assigned IPv6 address.
 *
 * ponytail: peer's port isn't carried by [DiscoveredDevice]/[PeerHandle] —
 * [WifiAwareNetworkInfo.getPort] (API 30+) is used once connected. Below API
 * 30 this throws; that's an acceptable gap given minSdk 29 already has known
 * Aware chipset-support caveats (see CLAUDE.md).
 */
@SuppressLint("MissingPermission")
class WifiAwareTransport(private val context: Context) : Transport {
    override val kind = TransportKind.WIFI_AWARE

    override suspend fun connect(peer: DiscoveredDevice): TransportHandle {
        val awareManager = checkNotNull(context.getSystemService(WifiAwareManager::class.java)) {
            "Wi-Fi Aware unavailable on this device"
        }
        check(awareManager.isAvailable) { "Wi-Fi Aware not currently available" }

        val session = attach(awareManager)
        val (discoverySession, peerHandle) = subscribeAndAwaitMatch(session)
        val (network, capabilities) = requestNetwork(discoverySession, peerHandle)
        val awareInfo = capabilities.transportInfo as? WifiAwareNetworkInfo
            ?: error("Wi-Fi Aware network established without transport info")
        val peerAddress = checkNotNull(awareInfo.peerIpv6Addr) { "Wi-Fi Aware peer address missing" }

        val socket = Socket()
        network.bindSocket(socket)
        socket.connect(InetSocketAddress(peerAddress, awareInfo.port), SOCKET_CONNECT_TIMEOUT_MS)
        discoverySession.close()
        return SocketTransportHandle(kind, socket) { session.close() }
    }

    private suspend fun attach(manager: WifiAwareManager): WifiAwareSession =
        suspendCancellableCoroutine { cont ->
            manager.attach(object : AttachCallback() {
                override fun onAttached(session: WifiAwareSession) = cont.resume(session)
                override fun onAttachFailed() = cont.resumeWithException(IllegalStateException("Wi-Fi Aware attach failed"))
            }, null)
        }

    private suspend fun subscribeAndAwaitMatch(session: WifiAwareSession): Pair<SubscribeDiscoverySession, PeerHandle> =
        suspendCancellableCoroutine { cont ->
            var subscribeSession: SubscribeDiscoverySession? = null
            val config = SubscribeConfig.Builder().setServiceName(SERVICE_NAME).build()
            session.subscribe(config, object : DiscoverySessionCallback() {
                override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                    subscribeSession = session
                }

                override fun onServiceDiscovered(peerHandle: PeerHandle, serviceSpecificInfo: ByteArray?, matchFilter: MutableList<ByteArray>?) {
                    val discovered = subscribeSession ?: return
                    if (cont.isActive) cont.resume(discovered to peerHandle)
                }

                override fun onSessionConfigFailed() =
                    cont.resumeWithException(IllegalStateException("Wi-Fi Aware subscribe config failed"))
            }, null)
        }

    private suspend fun requestNetwork(discoverySession: SubscribeDiscoverySession, peerHandle: PeerHandle): Pair<Network, NetworkCapabilities> =
        suspendCancellableCoroutine { cont ->
            val specifier = WifiAwareNetworkSpecifier.Builder(discoverySession, peerHandle).build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()
            val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (cont.isActive) cont.resume(network to networkCapabilities)
                }

                override fun onUnavailable() =
                    cont.resumeWithException(IllegalStateException("Wi-Fi Aware network request unavailable"))
            })
        }
}
