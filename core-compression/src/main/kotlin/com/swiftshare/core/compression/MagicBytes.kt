package com.swiftshare.core.compression

import java.io.File

/**
 * Sniffs a file's true type from its leading bytes (FR-04.1) — extensions lie,
 * magic bytes don't. Only lists formats that are already compressed (FR-04.2);
 * anything not recognized here is treated as compressible (FR-04.3).
 */
internal object MagicBytes {
    private val SIGNATURES: List<ByteArray> = listOf(
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), // JPEG
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), // PNG
        byteArrayOf(0x50, 0x4B, 0x03, 0x04), // ZIP (and docx/xlsx/pptx, already zip-compressed)
        byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()), // MKV/WebM (EBML)
        byteArrayOf(0x66, 0x74, 0x79, 0x70), // ftyp box (MP4/HEIC/MOV), offset 4
    )

    fun isAlreadyCompressed(file: File): Boolean {
        val header = readHeader(file, 12)
        if (header.size >= 8 && header.copyOfRange(4, 8).contentEquals(SIGNATURES[4])) return true
        return SIGNATURES.dropLast(1).any { sig -> header.size >= sig.size && header.copyOfRange(0, sig.size).contentEquals(sig) }
    }

    /** minSdk 29 doesn't have [java.io.InputStream.readNBytes] (API 33) — loop a plain [java.io.InputStream.read] instead. */
    private fun readHeader(file: File, maxBytes: Int): ByteArray = file.inputStream().use { stream ->
        val buffer = ByteArray(maxBytes)
        var total = 0
        while (total < maxBytes) {
            val read = stream.read(buffer, total, maxBytes - total)
            if (read < 0) break
            total += read
        }
        buffer.copyOf(total)
    }
}
