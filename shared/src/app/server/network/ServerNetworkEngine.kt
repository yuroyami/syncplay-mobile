package app.server.network

import app.server.ClientConnection
import app.server.SyncplayServer
import kotlinx.coroutines.CoroutineScope

/**
 * The TCP layer of the built-in server, with one implementation per platform.
 *
 * Android and desktop use Netty's ServerBootstrap, and iOS uses Ktor raw sockets. A browser tab
 * cannot listen on a port, so the web version only refuses. Each real implementation listens on
 * a port, accepts connections and passes each incoming JSON line to
 * [ClientConnection.handlePacket], which decodes it with [app.protocol.WireMessageDeserializer].
 */
expect class ServerNetworkEngine(
    server: SyncplayServer,
    scope: CoroutineScope
) {
    /**
     * Starts listening for incoming TCP connections on [port]. Returns once the server socket is
     * bound and accepting connections.
     */
    suspend fun startListening(port: Int)

    /** Stops the server and closes all client connections. */
    fun stop()
}
