package app.server.network

import app.server.ClientConnection
import app.server.SyncplayServer
import kotlinx.coroutines.CoroutineScope

/**
 * Platform-agnostic TCP server engine interface.
 *
 * Android uses Netty's ServerBootstrap; iOS uses Ktor raw sockets.
 * Both provide the same contract: listen on a port, accept connections,
 * forward incoming JSON-per-line to [ClientConnection.handlePacket] which routes
 * via [app.server.protocol.incoming.IncomingMessageDeserializer].
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
