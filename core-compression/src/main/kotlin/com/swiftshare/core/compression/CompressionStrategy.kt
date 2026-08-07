package com.swiftshare.core.compression

import java.io.File
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Decides whether/how to losslessly compress a file before chunking (BRD
 * Module 04). Skips already-compressed formats (FR-04.2) and never re-encodes
 * media lossily (FR-04.4).
 */
interface CompressionStrategy {
    fun shouldCompress(file: File): Boolean
    fun compress(file: File): CompressionResult
}

data class CompressionResult(
    val outputFile: File,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
)

/** Default no-op strategy — kept for callers that want compression disabled entirely. */
class NoOpCompressionStrategy : CompressionStrategy {
    override fun shouldCompress(file: File): Boolean = false

    override fun compress(file: File): CompressionResult = CompressionResult(
        outputFile = file,
        originalSizeBytes = file.length(),
        compressedSizeBytes = file.length(),
    )
}

/**
 * Detects already-compressed formats by magic bytes (FR-04.1/FR-04.2) and
 * losslessly deflates everything else (FR-04.3/FR-04.4) — java.util.zip's
 * Deflater, no third-party compression dependency needed.
 *
 * ponytail: java.util.zip.Deflater over zstd — stdlib, zero new dependency,
 * good enough for text/log/office payloads. Swap for zstd if benchmarks show
 * deflate's ratio/speed falls short for the file mix actually seen in the wild.
 */
class DeflateCompressionStrategy : CompressionStrategy {
    override fun shouldCompress(file: File): Boolean = !MagicBytes.isAlreadyCompressed(file)

    override fun compress(file: File): CompressionResult {
        val outputFile = File(file.parentFile, "${file.name}.deflate")
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        file.inputStream().use { input ->
            DeflaterOutputStream(outputFile.outputStream(), deflater).use { output -> input.copyTo(output) }
        }
        deflater.end()
        return CompressionResult(
            outputFile = outputFile,
            originalSizeBytes = file.length(),
            compressedSizeBytes = outputFile.length(),
        )
    }
}
