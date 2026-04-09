package app.server.network

import app.server.ClientConnection
import app.server.SyncplayServer
import app.server.protocol.InboundMessageHandler
import app.utils.loggy
import kotlinx.coroutines.CoroutineScope

/**
 * Platform-agnostic TCP server engine interface.
 *
 * Android uses Netty's ServerBootstrap; iOS uses Ktor raw sockets.
 * Both provide the same contract: listen on a port, accept connections,
 * route incoming lines to [ClientConnection] via [InboundMessageHandler].
 */
expect class ServerNetworkEngine(
    server: SyncplayServer,
    scope: CoroutineScope
) {
    /**
     * Starts listening for incoming TCP connections on the given port.
     * Returns once the server socket is bound and accepting connections.
     */
    suspend fun startListening(port: Int)

    /**
     * Stops the server and closes all client connections.
     */
    fun stop()
}
