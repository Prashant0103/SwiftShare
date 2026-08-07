package com.swiftshare.core.compression

import java.io.File
import java.util.zip.InflaterInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeflateCompressionStrategyTest {
    private val strategy = DeflateCompressionStrategy()

    @Test
    fun `skips already-compressed formats by magic bytes`() {
        val jpeg = tempFile(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00))
        assertFalse(strategy.shouldCompress(jpeg))
    }

    @Test
    fun `compresses a plain text file losslessly`() {
        val original = "hello ".repeat(1000).toByteArray()
        val file = tempFile(original)
        assertTrue(strategy.shouldCompress(file))

        val result = strategy.compress(file)
        assertTrue(result.compressedSizeBytes < result.originalSizeBytes)

        val roundTripped = InflaterInputStream(result.outputFile.inputStream()).use { it.readBytes() }
        assertEquals(original.toList(), roundTripped.toList())
    }

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("compression-test", ".bin").apply { writeBytes(bytes); deleteOnExit() }
}
