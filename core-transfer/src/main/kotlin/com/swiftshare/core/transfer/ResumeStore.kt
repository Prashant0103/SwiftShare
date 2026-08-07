package com.swiftshare.core.transfer

import java.io.File

/**
 * Tracks which chunks of a file have already been acknowledged, persisted to a sidecar
 * file so a transfer interrupted mid-job (app closed, connection dropped) can resume from
 * the last acked chunk instead of restarting (FR-03.5). Expires after [sessionWindowMs]
 * (default 10 minutes per FR-03.5) so a stale sidecar from days ago doesn't silently skip chunks.
 */
class ResumeStore(private val sessionWindowMs: Long = 10 * 60 * 1000) {

    fun load(target: File): Set<Int> {
        val sidecar = sidecarFor(target)
        if (!sidecar.exists()) return emptySet()
        val lines = sidecar.readLines()
        val timestamp = lines.getOrNull(0)?.toLongOrNull() ?: return emptySet()
        if (System.currentTimeMillis() - timestamp > sessionWindowMs) {
            sidecar.delete()
            return emptySet()
        }
        return lines.drop(1).mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun save(target: File, ackedSequences: Set<Int>) {
        sidecarFor(target).writeText(
            buildString {
                append(System.currentTimeMillis()).append('\n')
                ackedSequences.forEach { append(it).append('\n') }
            },
        )
    }

    fun clear(target: File) {
        sidecarFor(target).delete()
    }

    private fun sidecarFor(target: File) = File(target.parentFile, "${target.name}.swiftshare-resume")
}
