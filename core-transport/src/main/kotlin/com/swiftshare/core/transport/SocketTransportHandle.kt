package com.swiftshare.core.transport

import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** [TransportHandle] over a plain connected [Socket] — shared by Wi-Fi Aware and Wi-Fi Direct (both hand off a socket once the radio-level connection exists). */
class SocketTransportHandle(
    override val kind: TransportKind,
    private val socket: Socket,
    private val onClose: () -> Unit = {},
) : TransportHandle {

    override suspend fun send(bytes: ByteArray) = withContext(Dispatchers.IO) {
        socket.getOutputStream().write(bytes)
        socket.getOutputStream().flush()
    }

    override fun receive(): Flow<ByteArray> = callbackFlow {
        val input = socket.getInputStream()
        val buffer = ByteArray(64 * 1024)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                trySend(buffer.copyOf(read))
            }
        } finally {
            close()
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    override suspend fun close() = withContext(Dispatchers.IO) {
        runCatching { socket.close() }
        onClose()
    }
}
