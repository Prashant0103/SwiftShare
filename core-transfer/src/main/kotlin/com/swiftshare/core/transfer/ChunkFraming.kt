package com.swiftshare.core.transfer

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Wire framing for [TransportHandle.send]/[receive]. A handle only moves raw
 * bytes, so every message here is self-describing (type tag + length) to
 * survive being split/merged across the underlying socket's read buffer
 * (BRD Module 03 has no framing FR of its own — this is the plumbing FR-03.2's
 * checksum-per-chunk and FR-03.8's whole-file checksum ride on top of).
 */
sealed interface Frame {
    data class FileHeader(
        val fileName: String,
        val fileSizeBytes: Long,
        val totalChunks: Int,
        val chunkSizeBytes: Int,
    ) : Frame
    data class Chunk(val sequence: Int, val sha256: ByteArray, val payload: ByteArray) : Frame
    data class ChunkAck(val sequence: Int, val ok: Boolean) : Frame
    data class FileTrailer(val sha256: ByteArray) : Frame
    data class TrailerAck(val ok: Boolean) : Frame
}

private const val TYPE_FILE_HEADER = 0
private const val TYPE_CHUNK = 1
private const val TYPE_CHUNK_ACK = 2
private const val TYPE_FILE_TRAILER = 3
private const val TYPE_TRAILER_ACK = 4
private const val SHA256_BYTES = 32

object ChunkFraming {

    fun encode(frame: Frame): ByteArray = when (frame) {
        is Frame.FileHeader -> {
            val nameBytes = frame.fileName.toByteArray(StandardCharsets.UTF_8)
            ByteBuffer.allocate(1 + 2 + nameBytes.size + 8 + 4 + 4).apply {
                put(TYPE_FILE_HEADER.toByte())
                putShort(nameBytes.size.toShort())
                put(nameBytes)
                putLong(frame.fileSizeBytes)
                putInt(frame.totalChunks)
                putInt(frame.chunkSizeBytes)
            }.array()
        }
        is Frame.Chunk -> ByteBuffer.allocate(1 + 4 + 4 + SHA256_BYTES + frame.payload.size).apply {
            put(TYPE_CHUNK.toByte())
            putInt(frame.sequence)
            putInt(frame.payload.size)
            put(frame.sha256)
            put(frame.payload)
        }.array()
        is Frame.ChunkAck -> ByteBuffer.allocate(1 + 4 + 1).apply {
            put(TYPE_CHUNK_ACK.toByte())
            putInt(frame.sequence)
            put(if (frame.ok) 1 else 0)
        }.array()
        is Frame.FileTrailer -> ByteBuffer.allocate(1 + SHA256_BYTES).apply {
            put(TYPE_FILE_TRAILER.toByte())
            put(frame.sha256)
        }.array()
        is Frame.TrailerAck -> ByteBuffer.allocate(1 + 1).apply {
            put(TYPE_TRAILER_ACK.toByte())
            put(if (frame.ok) 1 else 0)
        }.array()
    }

    /** Returns the decoded frame and how many bytes of [buf] it consumed, or null if [buf] doesn't yet hold a full frame. */
    fun tryDecode(buf: ByteArray): Pair<Frame, Int>? {
        if (buf.isEmpty()) return null
        val bb = ByteBuffer.wrap(buf)
        return when (buf[0].toInt()) {
            TYPE_FILE_HEADER -> {
                if (buf.size < 3) return null
                bb.position(1)
                val nameLen = bb.short.toInt() and 0xFFFF
                val fixedTail = 8 + 4 + 4
                if (buf.size < 1 + 2 + nameLen + fixedTail) return null
                val nameBytes = ByteArray(nameLen).also { bb.get(it) }
                val fileSize = bb.long
                val totalChunks = bb.int
                val chunkSize = bb.int
                Frame.FileHeader(String(nameBytes, StandardCharsets.UTF_8), fileSize, totalChunks, chunkSize) to bb.position()
            }
            TYPE_CHUNK -> {
                if (buf.size < 1 + 4 + 4) return null
                bb.position(1)
                val seq = bb.int
                val payloadLen = bb.int
                val total = 1 + 4 + 4 + SHA256_BYTES + payloadLen
                if (buf.size < total) return null
                val sha = ByteArray(SHA256_BYTES).also { bb.get(it) }
                val payload = ByteArray(payloadLen).also { bb.get(it) }
                Frame.Chunk(seq, sha, payload) to total
            }
            TYPE_CHUNK_ACK -> {
                if (buf.size < 1 + 4 + 1) return null
                bb.position(1)
                val seq = bb.int
                val ok = bb.get().toInt() != 0
                Frame.ChunkAck(seq, ok) to bb.position()
            }
            TYPE_FILE_TRAILER -> {
                if (buf.size < 1 + SHA256_BYTES) return null
                bb.position(1)
                val sha = ByteArray(SHA256_BYTES).also { bb.get(it) }
                Frame.FileTrailer(sha) to bb.position()
            }
            TYPE_TRAILER_ACK -> {
                if (buf.size < 1 + 1) return null
                bb.position(1)
                val ok = bb.get().toInt() != 0
                Frame.TrailerAck(ok) to bb.position()
            }
            else -> error("unknown frame type ${buf[0]}")
        }
    }
}

/**
 * Accumulates raw bytes from [com.swiftshare.core.transport.TransportHandle.receive]
 * (which is not frame-aligned) and yields complete [Frame]s as they become available.
 *
 * ponytail: reslices the whole backing array on every feed/consume instead of a ring
 * buffer. Chunk payloads are capped at [TransferJob.DEFAULT_CHUNK_SIZE_BYTES]-ish sizes
 * so this is a handful of copies per chunk, not a hot loop — revisit if profiling says otherwise.
 */
class FrameDecoder {
    private var buffer = ByteArray(0)

    fun feed(bytes: ByteArray): List<Frame> {
        buffer += bytes
        val frames = mutableListOf<Frame>()
        while (true) {
            val decoded = ChunkFraming.tryDecode(buffer) ?: break
            frames += decoded.first
            buffer = buffer.copyOfRange(decoded.second, buffer.size)
        }
        return frames
    }
}
