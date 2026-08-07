package com.swiftshare.core.transfer

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

internal fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

/** Whole-file checksum (FR-03.8) — streamed, so it doesn't hold the file in memory (NFR-03.3). */
internal fun sha256File(file: File): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest()
}

internal fun readChunk(file: File, sequence: Int, chunkSizeBytes: Int): ByteArray {
    RandomAccessFile(file, "r").use { raf ->
        raf.seek(sequence.toLong() * chunkSizeBytes)
        val remaining = (raf.length() - raf.filePointer).coerceAtMost(chunkSizeBytes.toLong()).toInt()
        val bytes = ByteArray(remaining)
        raf.readFully(bytes)
        return bytes
    }
}

/**
 * Writes chunks for one in-flight received file at their byte offset (so chunks arriving
 * out of order, or over different bonded handles, land in the right place — FR-03.4/NFR-03.2),
 * then verifies the whole-file checksum once all chunks are in (FR-03.8).
 */
internal class ReceivingFile(
    val outputFile: File,
    val fileSizeBytes: Long,
    private val totalChunks: Int,
    private val chunkSizeBytes: Int,
    initialSequences: Set<Int> = emptySet(),
) {
    private val raf = RandomAccessFile(outputFile, "rw")
    private val receivedSequences = initialSequences.toMutableSet()

    fun receivedSequences(): Set<Int> = receivedSequences.toSet()

    /** Verifies the per-chunk checksum (FR-03.2) and writes it if valid; returns false to signal a re-send (FR-03.3). */
    fun acceptChunk(sequence: Int, expectedSha256: ByteArray, payload: ByteArray): Boolean {
        if (!sha256(payload).contentEquals(expectedSha256)) return false
        raf.seek(sequence.toLong() * chunkSizeBytes)
        raf.write(payload)
        receivedSequences += sequence
        return true
    }

    fun bytesWritten(): Long = receivedSequences.size.toLong() * chunkSizeBytes

    fun finish(expectedWholeFileSha256: ByteArray): Boolean {
        raf.fd.sync()
        raf.close()
        return receivedSequences.size == totalChunks && sha256File(outputFile).contentEquals(expectedWholeFileSha256)
    }
}
