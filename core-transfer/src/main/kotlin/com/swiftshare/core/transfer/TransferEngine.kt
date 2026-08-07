package com.swiftshare.core.transfer

import com.swiftshare.core.transport.TransportHandle
import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * Chunk, encrypt, and stream a file over one or more transport handles, with
 * resume and integrity verification (BRD Module 03).
 */
interface TransferEngine {
    suspend fun send(job: TransferJob, handles: List<TransportHandle>): Flow<TransferProgress>
    suspend fun receive(handles: List<TransportHandle>): Flow<TransferProgress>
}

/** Distributes chunks across active transport handles (FR-03.4). */
interface ChunkScheduler {
    fun nextHandleFor(chunkSequence: Int): TransportHandle
}

data class TransferJob(
    val files: List<File>,
    val chunkSizeBytes: Int = DEFAULT_CHUNK_SIZE_BYTES,
) {
    companion object {
        const val DEFAULT_CHUNK_SIZE_BYTES = 4 * 1024 * 1024 // FR-03.1 default
    }
}

data class ChunkResult(
    val sequence: Int,
    val sha256: String,
    val verified: Boolean,
)

data class TransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val currentSpeedBytesPerSec: Long,
    val etaSeconds: Long,
)
