package com.swiftshare.app

import com.swiftshare.core.discovery.DiscoveredDevice
import com.swiftshare.core.security.PairingResult
import com.swiftshare.core.transfer.TransferJob
import com.swiftshare.core.transfer.TransferProgress
import com.swiftshare.core.transport.NegotiationResult
import com.swiftshare.core.transport.TransportHandle
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Plain-language failures only (NFR-06.2) — no raw exception text reaches [UiState.Failed]. */
sealed interface UiState {
    /** Launcher entry point: pick a role. Skipped when arriving from the share sheet (already sending). */
    data object Idle : UiState
    data object Scanning : UiState
    data object WaitingForSender : UiState
    data class Receiving(val progress: TransferProgress) : UiState
    data class ReceiveComplete(val outputPath: String) : UiState
    data class DeviceList(val devices: List<DiscoveredDevice>) : UiState
    data class Connecting(val peer: DiscoveredDevice) : UiState
    data class Connected(val peer: DiscoveredDevice) : UiState
    data class Transferring(val peer: DiscoveredDevice, val progress: TransferProgress) : UiState
    data class Complete(val peer: DiscoveredDevice) : UiState
    data class Failed(val message: String) : UiState
}

/**
 * Drives the share flow: scan (proximity-sorted, FR-06.2's list mode) ->
 * select device -> pair on first contact -> negotiate transport -> hand the
 * negotiated handles to [com.swiftshare.core.transfer.TransferEngine] for the
 * send + progress screen (FR-06.4), all with no separate connect button
 * (FR-06.3).
 *
 * ponytail: pairing auto-confirms (no user-facing code comparison) — the
 * ECDH-derived shared secret is only as trustworthy as the BLE advertisement
 * it rode in on, with nothing checking it's the intended peer. Add a
 * displayed-code confirmation step if MITM-resistance on first pairing
 * matters more than a one-tap flow.
 */
class ShareFlowController(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    var filesToSend: List<File> = emptyList(),
) {
    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    fun permissionDenied(message: String) {
        _state.value = UiState.Failed(message)
    }

    fun goIdle() {
        _state.value = UiState.Idle
    }

    /**
     * Receiving side: become a Wi-Fi Direct group owner, accept one sender, then
     * stream inbound chunks through [com.swiftshare.core.transfer.TransferEngine.receive].
     */
    fun startReceiving() {
        _state.value = UiState.WaitingForSender
        scope.launch {
            val handle = try {
                container.wifiDirectTransport.listen()
            } catch (e: Exception) {
                _state.value = UiState.Failed("Couldn't start receiving — turn Wi-Fi on and try again")
                return@launch
            }
            try {
                container.transferEngine.receive(listOf(handle)).collect { progress ->
                    _state.value = UiState.Receiving(progress)
                }
                _state.value = UiState.ReceiveComplete(container.receivedDirectory.absolutePath)
            } catch (e: Exception) {
                _state.value = UiState.Failed("Receiving failed — please try again")
            } finally {
                handle.close()
            }
        }
    }

    fun startScanning() {
        _state.value = UiState.Scanning
        scope.launch {
            val seen = mutableListOf<DiscoveredDevice>()
            try {
                // Inside the try with scan(): startAdvertising() throws the same way when there's no
                // BLE adapter, and outside it that escaped into the permission callback as a crash.
                container.deviceAdvertiser.startAdvertising(container.localCapabilities, container.pairingManager.localPublicKey())
                container.deviceScanner.scan().collect { device ->
                    seen.removeAll { it.id == device.id }
                    seen.add(device)
                    _state.value = UiState.DeviceList(seen.sortedByDescending { it.rssi })
                }
            } catch (e: Exception) {
                // scan() throws when there's no BLE adapter (emulator), Bluetooth is off, or the
                // scan itself fails. Uncaught, the coroutine died and the UI sat on the initial
                // spinner forever with no error — surface it instead.
                _state.value = UiState.Failed("Can't scan for devices — turn Bluetooth on and try again (BLE isn't available on emulators)")
                return@launch
            }
            // scan() closes itself after 60s with no new device (FR-01.7) — stop here instead of
            // leaving the spinner running forever; user retries once the other phone is ready.
            if (seen.isEmpty()) {
                _state.value = UiState.Failed("No devices found nearby — make sure the other phone has SwiftShare open, then retry")
            }
        }
    }

    fun selectDevice(peer: DiscoveredDevice) {
        scope.launch {
            _state.value = UiState.Connecting(peer)
            if (!container.pairingManager.isPaired(peer.id) && !pairWith(peer)) {
                _state.value = UiState.Failed("Couldn't pair with this device — please try again")
                return@launch
            }
            val totalBytes = filesToSend.sumOf { it.length() }
            when (val result = container.transportManager.negotiate(peer, fileSizeBytes = totalBytes)) {
                is NegotiationResult.Established -> {
                    _state.value = UiState.Connected(peer)
                    if (filesToSend.isNotEmpty()) sendFiles(peer, result.handles)
                }
                is NegotiationResult.Failed ->
                    _state.value = UiState.Failed("Couldn't connect to this device — ${plainLanguage(result.reason)}")
            }
        }
    }

    /** First-time pairing, auto-confirmed on both sides — see the class doc's ponytail note. */
    private suspend fun pairWith(peer: DiscoveredDevice): Boolean {
        val challenge = container.pairingManager.beginPairing(peer.id, peer.publicKey)
        return container.pairingManager.confirmPairing(challenge) is PairingResult.Success
    }

    private suspend fun sendFiles(peer: DiscoveredDevice, handles: List<TransportHandle>) {
        try {
            container.transferEngine.send(TransferJob(filesToSend), handles).collect { progress ->
                _state.value = UiState.Transferring(peer, progress)
            }
            _state.value = UiState.Complete(peer)
        } catch (e: Exception) {
            _state.value = UiState.Failed("Transfer failed — please try again")
        } finally {
            handles.forEach { it.close() }
        }
    }

    private fun plainLanguage(reason: String): String = when {
        reason.contains("no implemented transport") -> "no shared connection method"
        reason.contains("timed out") || reason.contains("timeout") -> "connection timed out"
        else -> "please try again"
    }
}
