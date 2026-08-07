package com.swiftshare.core.transfer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChunkFramingTest {

    @Test
    fun `decodes a chunk frame split arbitrarily across feeds`() {
        val payload = ByteArray(100) { it.toByte() }
        val encoded = ChunkFraming.encode(Frame.Chunk(sequence = 7, sha256 = sha256(payload), payload = payload))
        val decoder = FrameDecoder()

        val frames = encoded.toList().chunked(13).flatMap { decoder.feed(it.toByteArray()) }

        val chunk = frames.single() as Frame.Chunk
        assertEquals(7, chunk.sequence)
        assertArrayEquals(payload, chunk.payload)
    }

    @Test
    fun `does not decode a partial frame`() {
        val encoded = ChunkFraming.encode(Frame.FileHeader("a.txt", 100, 1, 100))
        assertNull(ChunkFraming.tryDecode(encoded.copyOf(encoded.size - 1)))
    }

    @Test
    fun `decodes back-to-back frames in one feed`() {
        val ack1 = ChunkFraming.encode(Frame.ChunkAck(0, true))
        val ack2 = ChunkFraming.encode(Frame.ChunkAck(1, false))
        val decoder = FrameDecoder()

        val frames = decoder.feed(ack1 + ack2)

        assertEquals(listOf(Frame.ChunkAck(0, true), Frame.ChunkAck(1, false)), frames)
    }
}
