package app.protocol.network

import android.net.TrafficStats
import app.protocol.models.ConnectionState
import app.room.RoomViewmodel
import app.utils.loggy
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.DelimiterBasedFrameDecoder
import io.netty.handler.codec.Delimiters
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.handler.ssl.SslContextBuilder
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * Netty-based [NetworkManager] for Android: async TCP socket with TLS support.
 * Default network engine on Android.
 */
class NettyNetworkManager(viewmodel: RoomViewmodel) : NetworkManager(viewmodel) {

    override val engine = NetworkEngine.NETTY

    @Volatile
    private var channel: Channel? = null

    /**
     * Event loop group backing [channel]. Must be shut down together with the channel:
     * each group owns native NIO threads, so a group released later than its channel leaks
     * those threads for the process lifetime.
     */
    private var group: EventLoopGroup? = null

    /** Channel pipeline; the SSL handler is inserted here during TLS upgrade. */
    lateinit var pipeline: ChannelPipeline

    /**
     * Opens a TCP connection to the Syncplay server. Bootstraps a NIO client with string
     * codecs, a CRLF line-frame decoder, and an inbound handler that forwards each line to
     * [handlePacket]. Waits up to 10 s; a refused, unreachable or timed-out connect throws,
     * which [connect] turns into onConnectionFailed.
     */
    override suspend fun connectSocket() {
        val group: EventLoopGroup = NioEventLoopGroup()
        this.group = group
        val b = Bootstrap()
        b.group(group)
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    pipeline = ch.pipeline()
                    // 64 KiB line cap, matching the built-in server's framer. Must stay large
                    // enough for a fat List response (big room plus a playlist near the protocol's
                    // 10000-char limit); a smaller cap overflows the decoder and loops reconnects.
                    pipeline.addLast("framer", DelimiterBasedFrameDecoder(65536, *Delimiters.lineDelimiter()))
                    pipeline.addLast(StringDecoder())
                    pipeline.addLast(StringEncoder())
                    pipeline.addLast(object : SimpleChannelInboundHandler<String>() {
                        override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
                            super.userEventTriggered(ctx, evt)
                            loggy("Channel event: ${evt.toString()}")
                        }

                        override fun channelRead0(ctx: ChannelHandlerContext?, msg: String?) {
                            if (msg != null) handlePacket(msg)
                        }

                        override fun channelInactive(ctx: ChannelHandlerContext) {
                            super.channelInactive(ctx)
                            lost(ctx.channel())
                        }

                        @Deprecated("Deprecated in Java")
                        override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
                            loggy("EXCEPTION CAUGHT IN NETTY: ${cause?.stackTraceToString()}")
                            ctx?.close()
                        }
                    })
                }
            })

        TrafficStats.setThreadStatsTag(0xF00DFAF) // Satisfies Android's StrictMode policy

        val connected = withTimeout(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine<Channel> { cont ->
                val f = b.connect(viewmodel.session.serverHost, viewmodel.session.serverPort)
                f.addListener { future ->
                    // The future completing is not the same as it succeeding: a refused or
                    // unreachable host completes it with a cause.
                    if (future.isSuccess) cont.resume(f.channel())
                    else cont.resumeWithException(future.cause() ?: IOException("Connect failed"))
                }
                cont.invokeOnCancellation { f.cancel(true) }
            }
        }
        channel = connected
        loggy("$connected")
    }

    /**
     * The socket went away under us. Only the current channel counts: our own teardown of a
     * previous socket is not news. In CONNECTING that is a server closing mid-handshake (a
     * wrong password, for one), which used to leave the room dead-ended with no callback.
     */
    private fun lost(ch: Channel) {
        if (ch !== channel) return
        channel = null
        when (state.value) {
            ConnectionState.CONNECTING -> viewmodel.callback.onConnectionFailed()
            ConnectionState.CONNECTED -> viewmodel.callback.onDisconnected()
            else -> Unit
        }
    }

    /** Closes the channel and shuts down [group], releasing its NIO threads. Never blocks the caller. */
    override fun terminateExistingConnection() {
        val ch = channel
        channel = null
        try {
            loggy("Terminating network session.")
            ch?.close()
        } catch (e: Exception) {
            loggy("Channel close failed: ${e.message}")
        } finally {
            // Release the NIO threads with the channel — see [group].
            group?.shutdownGracefully()
            group = null
        }
    }

    /** Writes and flushes [s], returning once Netty has written it and throwing when it could not. */
    override suspend fun writeActualString(s: String) {
        val ch = channel ?: throw SocketGoneException()
        suspendCancellableCoroutine<Unit> { cont ->
            ch.writeAndFlush(s).addListener { future ->
                if (future.isSuccess) cont.resume(Unit)
                else cont.resumeWithException(future.cause() ?: IOException("Write failed"))
            }
        }
    }

    override fun supportsTLS() = true

    /**
     * Inserts an SSL handler at the front of the pipeline and suspends until the TLS handshake
     * completes (or fails). Awaiting the handshake guarantees a subsequent `Hello` write goes out
     * as ciphertext rather than relying on the SSL handler's buffering as a timing detail.
     *
     * The certificate is checked against the host name the user typed (SNI carries it too), not
     * the IP the socket dialled: without that check any certificate from anyone on the path
     * passed, and encryption bought nothing.
     */
    override suspend fun upgradeTls() = suspendCancellableCoroutine<Unit> { cont ->
        try {
            val sslContext = SslContextBuilder
                .forClient()
                .startTls(false)
                .build()

            val peerHost = viewmodel.session.tlsPeerHost
            val handler = sslContext.newHandler(
                pipeline.channel().alloc(),
                peerHost,
                viewmodel.session.serverPort
            )
            handler.engine().apply {
                sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            }
            pipeline.addFirst(handler)
            handler.handshakeFuture().addListener { future ->
                if (future.isSuccess) cont.resume(Unit)
                else cont.resumeWithException(future.cause() ?: Exception("TLS handshake failed"))
            }
        } catch (e: Throwable) {
            cont.resumeWithException(e)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
