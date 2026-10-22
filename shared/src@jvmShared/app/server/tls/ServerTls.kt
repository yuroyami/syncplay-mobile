package app.server.tls

import io.netty.channel.Channel
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.io.File

/** Where the host keeps its certificate: a folder in the app's own storage. */
internal expect fun hostTlsDirectory(): File

/**
 * TLS for the Netty server engines. A Syncplay connection starts in plain text and switches the
 * same socket to TLS when the client asks, so the switch happens in the middle of a connection.
 */
internal object ServerTls {

    @Volatile
    private var context: SslContext? = null

    val identity: HostCertificate.Identity by lazy { HostCertificate.loadOrCreate(hostTlsDirectory()) }

    private fun context(): SslContext = context ?: synchronized(this) {
        context ?: SslContextBuilder.forServer(identity.key, identity.certificate).build().also { context = it }
    }

    /**
     * Sends [line], the answer that accepts TLS, in plain text, then puts TLS in front of
     * everything after it. Both happen in one step of the channel's event loop, so no read can
     * land between them: the client's first TLS bytes always reach the TLS handler.
     */
    fun upgrade(channel: Channel, line: String) {
        val handler = context().newHandler(channel.alloc())
        channel.eventLoop().execute {
            channel.writeAndFlush(line)
            channel.pipeline().addFirst("tls", handler)
        }
    }
}
