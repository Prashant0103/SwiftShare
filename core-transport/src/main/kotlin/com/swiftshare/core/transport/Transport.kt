package com.swiftshare.core.transport

import com.swiftshare.core.discovery.DiscoveredDevice
import kotlinx.coroutines.flow.Flow

/**
 * One radio-level transport (Wi-Fi Aware, Wi-Fi Direct, BLE-only, and later
 * Sidelink — BRD Module 07's design implication: new transports plug in here
 * without touching core-transfer's chunking/bonding logic).
 */
interface Transport {
    val kind: TransportKind

    suspend fun connect(peer: DiscoveredDevice): TransportHandle
}

enum class TransportKind {
    WIFI_AWARE,
    WIFI_DIRECT,
    BLE,
    // Reserved for BRD Module 07 — not implemented in v1.
    SIDELINK,
}

/** An established, ready-to-use link, handed off to core-transfer (BRD Module 02 → 03). */
interface TransportHandle {
    val kind: TransportKind

    suspend fun send(bytes: ByteArray)

    fun receive(): Flow<ByteArray>

    suspend fun close()
}

/**
 * Picks the fastest mutually-supported transport(s) in priority order (BRD
 * Module 02, FR-02.2/FR-02.3/FR-02.5) and exposes negotiation state to the UI
 * (FR-02.6).
 */
interface TransportManager {

    suspend fun negotiate(peer: DiscoveredDevice, fileSizeBytes: Long): NegotiationResult

    fun state(): Flow<NegotiationState>
}

sealed interface NegotiationResult {
    data class Established(val handles: List<TransportHandle>) : NegotiationResult
    data class Failed(val reason: String) : NegotiationResult
}

enum class NegotiationState {
    CONNECTING,
    CONNECTED,
    FALLBACK_IN_PROGRESS,
    FAILED,
}
