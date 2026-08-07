package com.swiftshare.core.transfer

import com.swiftshare.core.transport.TransportHandle
import com.swiftshare.core.transport.TransportKind
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** In-memory duplex [TransportHandle] pair — stands in for a real socket for these tests. */
private class PipeHandle : TransportHandle {
    override val kind = TransportKind.WIFI_DIRECT
    lateinit var peer: PipeHandle
    private val inbound = Channel<ByteArray>(Channel.UNLIMITED)

    override suspend fun send(bytes: ByteArray) {
        peer.inbound.send(bytes)
    }

    override fun receive(): Flow<ByteArray> = inbound.receiveAsFlow()

    override suspend fun close() {
        peer.inbound.close()
    }
}

private fun pipePair(): Pair<PipeHandle, PipeHandle> {
    val a = PipeHandle()
    val b = PipeHandle()
    a.peer = b
    b.peer = a
    return a to b
}

class DefaultTransferEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `receiver reconstructs a multi-chunk file byte for byte`() = runTest {
        val sourceFile = tempFolder.newFile("source.txt")
        sourceFile.writeBytes(ByteArray(1000) { (it % 251).toByte() })
        val outputDir = tempFolder.newFolder("received")

        val (senderHandle, receiverHandle) = pipePair()
        val sender = DefaultTransferEngine(outputDirectory = tempFolder.newFolder("unused"))
        val receiver = DefaultTransferEngine(outputDirectory = outputDir)

        val receivedProgress = mutableListOf<TransferProgress>()
        val receiveJob = launch {
            receiver.receive(listOf(receiverHandle)).toList(receivedProgress)
        }

        val job = TransferJob(files = listOf(sourceFile), chunkSizeBytes = 64)
        val sentProgress = sender.send(job, listOf(senderHandle)).toList()
        senderHandle.close()
        receiveJob.join()

        val outputFile = File(outputDir, sourceFile.name)
        assertArrayEquals(sourceFile.readBytes(), outputFile.readBytes())
        assertTrue(sentProgress.isNotEmpty())
        assertTrue(receivedProgress.isNotEmpty())
        assertTrue(sentProgress.last().bytesTransferred == sourceFile.length())
    }

    @Test
    fun `resumes without re-sending chunks already written on both ends`() = runTest {
        val sourceFile = tempFolder.newFile("resumable.bin")
        val content = ByteArray(500) { it.toByte() }
        sourceFile.writeBytes(content)
        val outputDir = tempFolder.newFolder("received2")
        val outputFile = File(outputDir, sourceFile.name)

        // Simulate a prior session that got through chunks 0-2 (3 * 64 bytes) on both sides
        // before being interrupted: sender knows they were acked, receiver already has the bytes.
        val senderResumeStore = ResumeStore()
        val receiverResumeStore = ResumeStore()
        senderResumeStore.save(sourceFile, setOf(0, 1, 2))
        receiverResumeStore.save(outputFile, setOf(0, 1, 2))
        outputFile.writeBytes(content.copyOfRange(0, 64 * 3))

        val (senderHandle, receiverHandle) = pipePair()
        val sender = DefaultTransferEngine(outputDirectory = tempFolder.newFolder("unused2"), resumeStore = senderResumeStore)
        val receiver = DefaultTransferEngine(outputDirectory = outputDir, resumeStore = receiverResumeStore)

        val receiveJob = launch { receiver.receive(listOf(receiverHandle)).toList(mutableListOf()) }

        val job = TransferJob(files = listOf(sourceFile), chunkSizeBytes = 64)
        sender.send(job, listOf(senderHandle)).toList()
        senderHandle.close()
        receiveJob.join()

        assertArrayEquals(content, outputFile.readBytes())
    }
}
