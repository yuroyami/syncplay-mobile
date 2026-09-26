package app.server

import kotlinx.atomicfu.atomic

/**
 * What the hosted server admits, and what it holds for one client. The server hosts a few
 * friends, so the numbers leave room for that and stop a peer that opens sockets or queues data
 * without end. The network engines apply them.
 */
object ServerLimits {
    /** Connected sockets, joined or still in the handshake. A socket past this is closed at once. */
    const val MAX_CLIENTS = 32

    /** Bytes held for one client in one direction: lines not handled yet, or not sent yet. */
    const val MAX_PENDING_BYTES = 256 * 1024
}

/** Counts the bytes held for one client in one direction. Safe to use from any thread. */
class ByteBudget(private val limit: Int = ServerLimits.MAX_PENDING_BYTES) {
    private val used = atomic(0)

    /** Adds [bytes]. False when that passes the limit: the caller drops the client. */
    fun take(bytes: Int): Boolean = used.addAndGet(bytes) <= limit

    /** Gives back [bytes] once they are handled or sent. */
    fun give(bytes: Int) {
        used.addAndGet(-bytes)
    }
}
