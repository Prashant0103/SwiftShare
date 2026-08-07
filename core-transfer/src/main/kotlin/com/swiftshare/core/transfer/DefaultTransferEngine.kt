package com.swiftshare.core.transfer

import com.swiftshare.core.compression.CompressionStrategy
import com.swiftshare.core.compression.NoOpCompressionStrategy
import com.swiftshare.core.transport.TransportHandle
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Chunks files, streams them over one or more bonded [TransportHandle]s, verifies every
 * chunk and the whole file, and resumes an interrupted job from the last acked chunk
 * (BRD Module 03). Compression (Module 04) and encryption (Module 05) are cross-cutting
 * concerns invoked from here but owned by their own modules.
 *
 * ponytail: [compressionStrategy] defaults to [NoOpCompressionStrategy] — real magic-byte
 * detection lives in core-compression and slots in here once implemented, no change needed
 * on this side.
 */
class DefaultTransferEngine(
    private val outputDirectory: File,
    private val compressionStrategy: CompressionStrategy = NoOpCompressionStrategy(),
    private val resumeStore: ResumeStore = ResumeStore(),
    private val chunkAckTimeoutMs: Long = 5_000,
    private val maxAttemptsPerChunk: Int = 3,
) : TransferEngine {

    override suspend fun send(job: TransferJob, handles: List<TransportHandle>): Flow<TransferProgress> = flow {
        require(handles.isNotEmpty()) { "at least one transport handle is required" }
        coroutineScope {
            val routers = handles.associateWith { AckRouter(it, this) }
            val scheduler = RoundRobinChunkScheduler(handles)
            val totalBytes = job.files.sumOf { it.length() }
            var bytesDoneAcrossFiles = 0L
            val jobStart = System.currentTimeMillis()

            for (file in job.files) {
                val toSend = if (compressionStrategy.shouldCompress(file)) {
                    compressionStrategy.compress(file).outputFile
                } else {
                    file
                }
                val fileSize = toSend.length()
                val totalChunks = ceil(fileSize / job.chunkSizeBytes.toDouble()).toInt().coerceAtLeast(1)
                val alreadyAcked = resumeStore.load(toSend).toMutableSet()
                bytesDoneAcrossFiles += alreadyAcked.size.toLong() * job.chunkSizeBytes

                handles[0].send(
                    ChunkFraming.encode(Frame.FileHeader(file.name, fileSize, totalChunks, job.chunkSizeBytes)),
                )

                for (sequence in 0 until totalChunks) {
                    if (sequence in alreadyAcked) continue
                    val payload = readChunk(toSend, sequence, job.chunkSizeBytes)
                    val handle = scheduler.nextHandleFor(sequence)
                    val acked = sendChunkWithRetry(routers[handle]!!, handle, sequence, payload)
                    if (!acked) error("chunk $sequence of ${file.name} failed after $maxAttemptsPerChunk attempts")

                    alreadyAcked += sequence
                    resumeStore.save(toSend, alreadyAcked)
                    bytesDoneAcrossFiles += payload.size
                    emit(progress(bytesDoneAcrossFiles, totalBytes, jobStart))
                }

                val trailerOk = sendTrailerWithRetry(routers[handles[0]]!!, handles[0], sha256File(toSend))
                if (!trailerOk) error("whole-file verification failed for ${file.name}")
                resumeStore.clear(toSend)
            }

            routers.values.forEach { it.stop() }
        }
    }

    override suspend fun receive(handles: List<TransportHandle>): Flow<TransferProgress> = flow {
        require(handles.isNotEmpty()) { "at least one transport handle is required" }
        outputDirectory.mkdirs()

        var currentFile: ReceivingFile? = null
        var bytesDoneAcrossFiles = 0L
        val jobStart = System.currentTimeMillis()

        val framesByHandle = handles.map { handle -> handle.decodedFrames().map { handle to it } }.merge()
        framesByHandle.collect { (handle, frame) ->
            when (frame) {
                is Frame.FileHeader -> {
                    val outputFile = File(outputDirectory, frame.fileName)
                    val alreadyReceived = resumeStore.load(outputFile)
                    currentFile = ReceivingFile(
                        outputFile,
                        frame.fileSizeBytes,
                        frame.totalChunks,
                        frame.chunkSizeBytes,
                        alreadyReceived,
                    )
                    bytesDoneAcrossFiles += alreadyReceived.size.toLong() * frame.chunkSizeBytes
                }
                is Frame.Chunk -> {
                    val file = currentFile ?: return@collect
                    val ok = file.acceptChunk(frame.sequence, frame.sha256, frame.payload)
                    handle.send(ChunkFraming.encode(Frame.ChunkAck(frame.sequence, ok)))
                    if (ok) {
                        resumeStore.save(file.outputFile, file.receivedSequences())
                        bytesDoneAcrossFiles += frame.payload.size
                        emit(progress(bytesDoneAcrossFiles, maxOf(bytesDoneAcrossFiles, file.fileSizeBytes), jobStart))
                    }
                }
                is Frame.FileTrailer -> {
                    val file = currentFile ?: return@collect
                    val ok = file.finish(frame.sha256)
                    handle.send(ChunkFraming.encode(Frame.TrailerAck(ok)))
                    if (ok) {
                        resumeStore.clear(file.outputFile)
                        currentFile = null
                    }
                }
                else -> Unit
            }
        }
    }

    private suspend fun sendChunkWithRetry(
        router: AckRouter,
        handle: TransportHandle,
        sequence: Int,
        payload: ByteArray,
    ): Boolean {
        val sha = sha256(payload)
        repeat(maxAttemptsPerChunk) {
            handle.send(ChunkFraming.encode(Frame.Chunk(sequence, sha, payload)))
            if (router.awaitChunkAck(sequence, chunkAckTimeoutMs) == true) return true
        }
        return false
    }

    private suspend fun sendTrailerWithRetry(router: AckRouter, handle: TransportHandle, wholeFileSha: ByteArray): Boolean {
        repeat(maxAttemptsPerChunk) {
            handle.send(ChunkFraming.encode(Frame.FileTrailer(wholeFileSha)))
            if (router.awaitTrailerAck(chunkAckTimeoutMs) == true) return true
        }
        return false
    }

    private fun progress(bytesDone: Long, totalBytes: Long, jobStart: Long): TransferProgress {
        val elapsedMs = (System.currentTimeMillis() - jobStart).coerceAtLeast(1)
        val speed = bytesDone * 1000 / elapsedMs
        val eta = if (speed > 0) (totalBytes - bytesDone) / speed else 0
        return TransferProgress(bytesDone, totalBytes, speed, eta)
    }
}

/** Decodes this handle's incoming byte stream into [Frame]s, one decoder instance per subscription. */
private fun TransportHandle.decodedFrames(): Flow<Frame> = flow {
    val decoder = FrameDecoder()
    receive().collect { bytes -> decoder.feed(bytes).forEach { emit(it) } }
}

/**
 * Routes incoming ack frames for one handle back to the coroutine awaiting that specific
 * chunk (or the trailer), so a bonded transfer can have multiple chunks in flight across
 * different handles at once without acks crossing streams.
 */
private class AckRouter(handle: TransportHandle, scope: CoroutineScope) {
    private val pendingChunkAcks = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()
    private val pendingTrailerAcks = ConcurrentHashMap.newKeySet<CompletableDeferred<Boolean>>()

    private val listener = scope.launch {
        handle.decodedFrames().collect { frame ->
            when (frame) {
                is Frame.ChunkAck -> pendingChunkAcks.remove(frame.sequence)?.complete(frame.ok)
                is Frame.TrailerAck -> pendingTrailerAcks.firstOrNull()?.let {
                    pendingTrailerAcks.remove(it)
                    it.complete(frame.ok)
                }
                else -> Unit
            }
        }
    }

    suspend fun awaitChunkAck(sequence: Int, timeoutMs: Long): Boolean? {
        val deferred = CompletableDeferred<Boolean>()
        pendingChunkAcks[sequence] = deferred
        return withTimeoutOrNull(timeoutMs) { deferred.await() }.also { pendingChunkAcks.remove(sequence) }
    }

    suspend fun awaitTrailerAck(timeoutMs: Long): Boolean? {
        val deferred = CompletableDeferred<Boolean>()
        pendingTrailerAcks.add(deferred)
        return withTimeoutOrNull(timeoutMs) { deferred.await() }.also { pendingTrailerAcks.remove(deferred) }
    }

    fun stop() = listener.cancel()
}
