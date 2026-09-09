package app.server.network

import app.server.SyncplayServer
import app.utils.loggy
import kotlinx.coroutines.CoroutineScope

/**
 * Hosting is not something a page can do.
 *
 * A browser tab cannot listen on a port, so the built-in server has no web implementation and is
 * not offered in the UI there. The class exists because the shared server code names the type.
 *
 * If a web client should ever be reachable, the answer is the other way round: the Android or
 * desktop host grows a WebSocket listener beside its TCP one, and browsers connect to that.
 */
actual class ServerNetworkEngine actual constructor(
    @Suppress("UNUSED_PARAMETER") server: SyncplayServer,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
) {
    actual suspend fun startListening(port: Int) {
        loggy("Server hosting was requested on the web, where a page cannot listen on port $port.")
        throw UnsupportedOperationException("A browser tab cannot listen for connections.")
    }

    actual fun stop() = Unit
}
