package com.swiftshare.core.compression

import java.io.File

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

/** Default no-op strategy — real detection/zstd wiring is a later phase. */
class NoOpCompressionStrategy : CompressionStrategy {
    override fun shouldCompress(file: File): Boolean = false

    override fun compress(file: File): CompressionResult = CompressionResult(
        outputFile = file,
        originalSizeBytes = file.length(),
        compressedSizeBytes = file.length(),
    )
}
