package com.swiftshare.core.transport

import com.swiftshare.core.discovery.DeviceCapabilities
import com.swiftshare.core.discovery.DiscoveredDevice
import com.swiftshare.core.security.RotatingDeviceId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTransport(
    override val kind: TransportKind,
    private val behavior: suspend () -> TransportHandle,
) : Transport {
    override suspend fun connect(peer: DiscoveredDevice): TransportHandle = behavior()
}

private class FakeHandle(override val kind: TransportKind) : TransportHandle {
    override suspend fun send(bytes: ByteArray) = Unit
    override fun receive() = throw UnsupportedOperationException()
    override suspend fun close() = Unit
}

private fun peer(aware: Boolean, direct: Boolean) = DiscoveredDevice(
    id = RotatingDeviceId("peer"),
    capabilities = DeviceCapabilities("v1", supportsWifiAware = aware, supportsWifiDirect = direct, supportsUwb = false),
    rssi = -50,
    rangingInfo = null,
)

class DefaultTransportManagerTest {

    @Test
    fun `picks Wi-Fi Aware over Direct when peer supports both`() = runTest {
        val aware = FakeTransport(TransportKind.WIFI_AWARE) { FakeHandle(TransportKind.WIFI_AWARE) }
        val direct = FakeTransport(TransportKind.WIFI_DIRECT) { FakeHandle(TransportKind.WIFI_DIRECT) }
        val manager = DefaultTransportManager(listOf(direct, aware))

        val result = manager.negotiate(peer(aware = true, direct = true), fileSizeBytes = 10)

        val established = result as NegotiationResult.Established
        assertEquals(TransportKind.WIFI_AWARE, established.handles.single().kind)
    }

    @Test
    fun `falls back to Direct when Aware connect fails`() = runTest {
        val aware = FakeTransport(TransportKind.WIFI_AWARE) { error("Aware unavailable") }
        val direct = FakeTransport(TransportKind.WIFI_DIRECT) { FakeHandle(TransportKind.WIFI_DIRECT) }
        val manager = DefaultTransportManager(listOf(aware, direct))

        val result = manager.negotiate(peer(aware = true, direct = true), fileSizeBytes = 10)

        val established = result as NegotiationResult.Established
        assertEquals(TransportKind.WIFI_DIRECT, established.handles.single().kind)
    }

    @Test
    fun `falls back when Aware connect exceeds the timeout`() = runTest {
        val aware = FakeTransport(TransportKind.WIFI_AWARE) {
            delay(10_000)
            FakeHandle(TransportKind.WIFI_AWARE)
        }
        val direct = FakeTransport(TransportKind.WIFI_DIRECT) { FakeHandle(TransportKind.WIFI_DIRECT) }
        val manager = DefaultTransportManager(listOf(aware, direct), connectTimeoutMs = 100)

        val result = manager.negotiate(peer(aware = true, direct = true), fileSizeBytes = 10)

        val established = result as NegotiationResult.Established
        assertEquals(TransportKind.WIFI_DIRECT, established.handles.single().kind)
    }

    @Test
    fun `fails when no candidate transport is implemented for the peer's capabilities`() = runTest {
        val direct = FakeTransport(TransportKind.WIFI_DIRECT) { FakeHandle(TransportKind.WIFI_DIRECT) }
        val manager = DefaultTransportManager(listOf(direct))

        val result = manager.negotiate(peer(aware = false, direct = false), fileSizeBytes = 10)

        assertTrue(result is NegotiationResult.Failed)
    }

    @Test
    fun `bonds a second Direct handle alongside Aware for large files`() = runTest {
        val aware = FakeTransport(TransportKind.WIFI_AWARE) { FakeHandle(TransportKind.WIFI_AWARE) }
        val direct = FakeTransport(TransportKind.WIFI_DIRECT) { FakeHandle(TransportKind.WIFI_DIRECT) }
        val manager = DefaultTransportManager(listOf(aware, direct), bondingThresholdBytes = 100)

        val result = manager.negotiate(peer(aware = true, direct = true), fileSizeBytes = 200)

        val established = result as NegotiationResult.Established
        assertEquals(setOf(TransportKind.WIFI_AWARE, TransportKind.WIFI_DIRECT), established.handles.map { it.kind }.toSet())
    }

    @Test
    fun `does not bond a second handle for small files`() = runTest {
        val aware = FakeTransport(TransportKind.WIFI_AWARE) { FakeHandle(TransportKind.WIFI_AWARE) }
        val direct = FakeTransport(TransportKind.WIFI_DIRECT) { FakeHandle(TransportKind.WIFI_DIRECT) }
        val manager = DefaultTransportManager(listOf(aware, direct), bondingThresholdBytes = 100)

        val result = manager.negotiate(peer(aware = true, direct = true), fileSizeBytes = 10)

        val established = result as NegotiationResult.Established
        assertEquals(1, established.handles.size)
    }

    @Test
    fun `reports CONNECTED state after a successful negotiation`() = runTest {
        val aware = FakeTransport(TransportKind.WIFI_AWARE) { FakeHandle(TransportKind.WIFI_AWARE) }
        val manager = DefaultTransportManager(listOf(aware))

        manager.negotiate(peer(aware = true, direct = false), fileSizeBytes = 10)

        assertEquals(NegotiationState.CONNECTED, manager.state().first())
    }
}
