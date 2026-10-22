package app.server.network

import app.server.ServerLimits
import app.server.SyncplayServer
import app.server.model.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals

/** The desktop server engine closes a socket at once when the server already has its maximum. */
class AdmissionLimitTest {

    private fun freePort() = ServerSocket(0).use { it.localPort }

    /** -1 when the server closed the socket, 0 when it is still open after [waitMs]. */
    private fun readOrClosed(socket: Socket, waitMs: Int): Int = try {
        socket.soTimeout = waitMs
        if (socket.getInputStream().read() == -1) -1 else 1
    } catch (_: SocketTimeoutException) {
        0
    }

    @Test
    fun aSocketPastTheMaximumIsClosedAtOnce() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = ServerNetworkEngine(SyncplayServer(ServerConfig(), scope), scope)
        val port = freePort()
        engine.startListening(port)
        val sockets = mutableListOf<Socket>()
        try {
            repeat(ServerLimits.MAX_CLIENTS) {
                sockets += Socket().apply { connect(InetSocketAddress("127.0.0.1", port), 2_000) }
            }
            // The last admitted socket is still open.
            assertEquals(0, readOrClosed(sockets.last(), 300))
            val extra = Socket().apply { connect(InetSocketAddress("127.0.0.1", port), 2_000) }
            sockets += extra
            assertEquals(-1, readOrClosed(extra, 2_000), "the socket past the maximum is closed")
        } finally {
            sockets.forEach { runCatching { it.close() } }
            engine.stop()
            scope.cancel()
        }
    }
}
