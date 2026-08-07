package com.swiftshare.core.transfer

import com.swiftshare.core.transport.TransportHandle

/** Distributes chunks round-robin across active handles (FR-03.4). Single handle degrades to always returning it. */
class RoundRobinChunkScheduler(private val handles: List<TransportHandle>) : ChunkScheduler {
    init { require(handles.isNotEmpty()) { "at least one transport handle is required" } }

    override fun nextHandleFor(chunkSequence: Int): TransportHandle =
        handles[chunkSequence.mod(handles.size)]
}
