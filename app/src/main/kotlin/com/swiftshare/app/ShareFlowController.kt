package com.swiftshare.app

import com.swiftshare.core.discovery.DiscoveredDevice
import com.swiftshare.core.transport.NegotiationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Plain-language failures only (NFR-06.2) — no raw exception text reaches [UiState.Failed]. */
sealed interface UiState {
    data object Scanning : UiState
    data class DeviceList(val devices: List<DiscoveredDevice>) : UiState
    data class Connecting(val peer: DiscoveredDevice) : UiState
    data class Connected(val peer: DiscoveredDevice) : UiState
    data class Failed(val message: String) : UiState
}

/**
 * Drives the share flow: scan (proximity-sorted, FR-06.2's list mode) ->
 * select device -> negotiate transport in the background with no separate
 * connect button (FR-06.3).
 *
 * Stops at [UiState.Connected] — handing off to [com.swiftshare.core.transfer]
 * for the progress screen (FR-06.4) is a separate slice.
 *
 * ponytail: FR-06.5's pairing-confirmation screen for first-time devices is
 * NOT built here. It needs [com.swiftshare.core.security.PairingManager]'s
 * beginPairing(peerPublicKey), but nothing exchanges that public key yet —
 * [com.swiftshare.core.discovery.AdvertisementCodec] only carries capability
 * flags + a session id, no room for a key. Add the pairing screen once
 * Module 01/02 grows a channel for it; until then, an unpaired device fails
 * with a plain-language message rather than faking the flow.
 */
class ShareFlowController(
    private val container: AppContainer,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UiState>(UiState.Scanning)
    val state: StateFlow<UiState> = _state

    fun startScanning() {
        _state.value = UiState.Scanning
        scope.launch {
            val seen = mutableListOf<DiscoveredDevice>()
            container.deviceScanner.scan().collect { device ->
                seen.removeAll { it.id == device.id }
                seen.add(device)
                _state.value = UiState.DeviceList(seen.sortedByDescending { it.rssi })
            }
        }
    }

    fun selectDevice(peer: DiscoveredDevice) {
        scope.launch {
            _state.value = UiState.Connecting(peer)
            if (!container.pairingManager.isPaired(peer.id)) {
                _state.value = UiState.Failed("Pairing with a new device isn't supported yet — try a previously-paired device")
                return@launch
            }
            when (val result = container.transportManager.negotiate(peer, fileSizeBytes = 0)) {
                is NegotiationResult.Established -> _state.value = UiState.Connected(peer)
                is NegotiationResult.Failed ->
                    _state.value = UiState.Failed("Couldn't connect to this device — ${plainLanguage(result.reason)}")
            }
        }
    }

    private fun plainLanguage(reason: String): String = when {
        reason.contains("no implemented transport") -> "no shared connection method"
        reason.contains("timed out") || reason.contains("timeout") -> "connection timed out"
        else -> "please try again"
    }
}
