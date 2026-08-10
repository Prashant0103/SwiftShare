package com.swiftshare.core.transport

import com.swiftshare.core.discovery.DeviceCapabilities
import com.swiftshare.core.discovery.DiscoveredDevice
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeout

/**
 * Picks transport by BRD priority (FR-02.2: Aware > Direct > BLE — matches
 * [TransportKind]'s declaration order, so sorting by ordinal is the priority
 * list), falls back within [connectTimeoutMs] on failure (FR-02.5), bonds a
 * second handle for large files when Aware succeeds (FR-02.3), and reports
 * state live (FR-02.6).
 *
 * ponytail: FR-02.3's "secondary concurrent Wi-Fi band" check is simplified
 * to "Wi-Fi Direct is also a candidate" — no separate radio-concurrency probe.
 * Upgrade if bonded transfers show contention instead of a speedup.
 */
class DefaultTransportManager(
    private val transports: List<Transport>,
    private val bondingThresholdBytes: Long = 100L * 1024 * 1024,
    // Wi-Fi Direct peer discovery + group formation realistically takes 5-15s on real hardware;
    // the old 5s budget timed out every Direct attempt before the group ever formed.
    private val connectTimeoutMs: Long = 25_000,
) : TransportManager {

    private val _state = MutableStateFlow(NegotiationState.CONNECTING)

    override fun state(): Flow<NegotiationState> = _state

    override suspend fun negotiate(peer: DiscoveredDevice, fileSizeBytes: Long): NegotiationResult {
        val candidates = transports
            .filter { it.kind.isSupportedBy(peer.capabilities) }
            .sortedBy { it.kind.ordinal }

        if (candidates.isEmpty()) {
            _state.value = NegotiationState.FAILED
            return NegotiationResult.Failed("peer and this device share no implemented transport")
        }

        candidates.forEachIndexed { index, transport ->
            _state.value = if (index == 0) NegotiationState.CONNECTING else NegotiationState.FALLBACK_IN_PROGRESS
            val primary = tryConnect(transport, peer) ?: return@forEachIndexed
            val secondary = bondedSecondHandle(transport, candidates, peer, fileSizeBytes)
            _state.value = NegotiationState.CONNECTED
            return NegotiationResult.Established(listOfNotNull(primary, secondary))
        }

        _state.value = NegotiationState.FAILED
        return NegotiationResult.Failed("all candidate transports failed or timed out")
    }

    private suspend fun tryConnect(transport: Transport, peer: DiscoveredDevice): TransportHandle? =
        try {
            withTimeout(connectTimeoutMs) { transport.connect(peer) }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: Exception) {
            null
        }

    private suspend fun bondedSecondHandle(
        primary: Transport,
        candidates: List<Transport>,
        peer: DiscoveredDevice,
        fileSizeBytes: Long,
    ): TransportHandle? {
        if (primary.kind != TransportKind.WIFI_AWARE || fileSizeBytes < bondingThresholdBytes) return null
        val secondary = candidates.firstOrNull { it.kind == TransportKind.WIFI_DIRECT } ?: return null
        return tryConnect(secondary, peer)
    }
}

private fun TransportKind.isSupportedBy(capabilities: DeviceCapabilities): Boolean = when (this) {
    TransportKind.WIFI_AWARE -> capabilities.supportsWifiAware
    TransportKind.WIFI_DIRECT -> capabilities.supportsWifiDirect
    // ponytail: BLE-only tiny-payload tier (FR-02.2 tier 3) needs a GATT data
    // channel that doesn't exist yet — no Transport implements TransportKind.BLE.
    // Add when there's an actual tiny-payload (text/contact card) use case.
    TransportKind.BLE -> false
    TransportKind.SIDELINK -> false
}
