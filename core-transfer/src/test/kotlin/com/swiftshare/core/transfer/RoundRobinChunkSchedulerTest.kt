package com.swiftshare.core.transfer

import com.swiftshare.core.transport.TransportHandle
import com.swiftshare.core.transport.TransportKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeHandle : TransportHandle {
    override val kind = TransportKind.WIFI_DIRECT
    override suspend fun send(bytes: ByteArray) = Unit
    override fun receive(): Flow<ByteArray> = emptyFlow()
    override suspend fun close() = Unit
}

class RoundRobinChunkSchedulerTest {

    @Test
    fun `cycles through handles in order`() {
        val a = FakeHandle()
        val b = FakeHandle()
        val scheduler = RoundRobinChunkScheduler(listOf(a, b))

        assertEquals(a, scheduler.nextHandleFor(0))
        assertEquals(b, scheduler.nextHandleFor(1))
        assertEquals(a, scheduler.nextHandleFor(2))
        assertEquals(b, scheduler.nextHandleFor(3))
    }

    @Test
    fun `always returns the only handle when unbonded`() {
        val a = FakeHandle()
        val scheduler = RoundRobinChunkScheduler(listOf(a))

        assertEquals(a, scheduler.nextHandleFor(0))
        assertEquals(a, scheduler.nextHandleFor(5))
    }
}
