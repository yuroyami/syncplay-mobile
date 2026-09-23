package app.server.network

import app.server.SyncplayServer
import app.utils.loggy
import kotlinx.coroutines.CoroutineScope

/**
 * A page cannot host a Syncplay server.
 *
 * A browser tab cannot listen on a port, so the hosted server has no web implementation. The
 * class exists because the shared server code names the type, and [startListening] throws.
 *
 * To reach a web client, an Android or desktop host needs a WebSocket listener beside its TCP
 * one; browsers then connect to that listener.
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
