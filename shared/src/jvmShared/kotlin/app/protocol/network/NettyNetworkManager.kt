package app.protocol.network

import app.protocol.models.ConnectionState
import app.room.RoomViewmodel
import app.utils.loggy
import io.netty.bootstrap.Bootstrap
import java.util.concurrent.TimeUnit
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.DelimiterBasedFrameDecoder
import io.netty.handler.codec.Delimiters
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Netty-based [NetworkManager] for the JVM platforms: async TCP socket with TLS support. The
 * default network engine on both Android and desktop.
 *
 * This used to be two byte-identical files, which meant every Netty fix in the ledger had to land
 * twice. The only real differences were socket tagging and waiting for Conscrypt, and both are
 * behind [tagSocketThread] and [awaitTlsProviderReady] now.
 */
class NettyNetworkManager(viewmodel: RoomViewmodel) : NetworkManager(viewmodel) {

    override val engine = NetworkEngine.NETTY

    @Volatile
    private var channel: Channel? = null

    /**
     * Event loop group backing [channel]. Must be shut down together with the channel:
     * each group owns native NIO threads, so a group released later than its channel leaks
     * those threads for the process lifetime.
     *
     * Volatile for the same reason [channel] is: it is written on the connect coroutine and read
     * by [terminateExistingConnection], which the channel watchdog and the handshake deadline
     * both call from coroutines of their own.
     */
    @Volatile
    private var group: EventLoopGroup? = null

    /**
     * Opens a TCP connection to the Syncplay server. Bootstraps a NIO client with string
     * codecs, a CRLF line-frame decoder, and an inbound handler that forwards each line to
     * [handlePacket]. Waits up to 10 s; a refused, unreachable or timed-out connect throws,
     * which [connect] turns into onConnectionFailed.
     */
    override suspend fun connectSocket() {
        loggy("Handshake: entering the transport after ${sinceHandshakeStart()}")
        // One thread, explicitly. The no-argument constructor sizes the group at twice the core
        // count, which on a phone spins up sixteen NIO threads to service the one socket this
        // client ever opens, and does it again on every reconnect attempt.
        val group: EventLoopGroup = NioEventLoopGroup(1)
        this.group = group
        val b = Bootstrap()
        b.group(group)
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val pipeline = ch.pipeline()
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
                            // A line from a socket we have already replaced is not ours to act on.
                            if (msg != null && ctx?.channel() === channel) handlePacket(msg)
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

        tagSocketThread()
        loggy("Handshake: bootstrap ready after ${sinceHandshakeStart()}, dialling ${viewmodel.session.serverHost}:${viewmodel.session.serverPort}")

        val connected = try {
            dial(b).also { loggy("Handshake: dial returned after ${sinceHandshakeStart()}") }
        } catch (e: Throwable) {
            loggy("Handshake: dial failed after ${sinceHandshakeStart()} (${e::class.simpleName}: ${e.message})")
            /* A dial that fails leaves this group with nothing to serve. It used to sit there
             * holding its NIO thread until the next connect attempt tore it down on the way in.
             * Shut down unconditionally: if a newer attempt has already claimed the field, this
             * group is doubly orphaned and would otherwise never be released at all. */
            if (this.group === group) this.group = null
            runCatching { group.shutdownGracefully(0L, GROUP_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
            throw e
        }
        channel = connected
        loggy("$connected")
    }

    private suspend fun dial(b: Bootstrap): Channel =
        withTimeout(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine<Channel> { cont ->
                val f = b.connect(viewmodel.session.serverHost, viewmodel.session.serverPort)
                f.addListener { future ->
                    // The future completing is not the same as it succeeding: a refused or
                    // unreachable host completes it with a cause.
                    if (future.isSuccess) cont.resume(f.channel())
                    else cont.resumeWithException(future.cause() ?: IOException("Connect failed"))
                }
                cont.invokeOnCancellation {
                    // Losing the race means the connect already succeeded, so cancelling the
                    // future does nothing and the socket would be left open with nothing holding
                    // it: `channel` is only assigned after this block returns.
                    if (!f.cancel(true)) f.channel()?.close()
                }
            }
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
            /* Release the NIO threads with the channel — see [group]. No quiet period: the
             * channel is closed on the line above, so there is nothing to wind down, and the
             * default two seconds is longer than the shortest reconnect interval, which left
             * every retry running its group alongside the one before it. */
            group?.shutdownGracefully(0L, GROUP_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            group = null
        }
    }

    /** Writes and flushes [s], returning once Netty has written it and throwing when it could not. */
    override suspend fun writeActualString(s: String) {
        val ch = channel ?: throw SocketGoneException()
        suspendCancellableCoroutine<Unit> { cont ->
            val f = ch.writeAndFlush(s)
            f.addListener { future ->
                if (future.isSuccess) cont.resume(Unit)
                else cont.resumeWithException(future.cause() ?: IOException("Write failed"))
            }
            // The caller writes under a timeout. Without this the timeout only abandoned the
            // wait: the write stayed queued and still went out, so the caller's retry put the
            // same line on the wire a second time.
            cont.invokeOnCancellation { f.cancel(false) }
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
    override suspend fun upgradeTls() {
        /* Bounded, the way the SwiftNIO side already bounds its own handshake. This runs on the
         * serial inbound consumer, so a handshake that never settles stops every packet behind
         * it; the channel close from the handshake deadline does eventually fail the promise, but
         * waiting for the provider happens before any handler is in the pipeline, where closing
         * the channel cannot reach it. Measured against the official server the whole upgrade is
         * about a second.
         *
         * The timeout is turned into an ordinary exception on the way out. TimeoutCancellationException
         * is a CancellationException, and the caller rethrows those by contract, which would take
         * the inbound consumer down with it and leave the connection unable to read anything again. */
        try {
            withTimeout(TLS_UPGRADE_TIMEOUT_MS) {
                // Android has to wait for Conscrypt; desktop's JDK provider is always there.
                awaitTlsProviderReady()
                upgradeTlsNow()
            }
        } catch (e: TimeoutCancellationException) {
            throw IOException("TLS upgrade did not complete within ${TLS_UPGRADE_TIMEOUT_MS}ms", e)
        }
    }

    private suspend fun upgradeTlsNow() = suspendCancellableCoroutine<Unit> { cont ->
        try {
            // Off the current channel, not off a field the ChannelInitializer wrote: that field
            // tracked whichever channel was initialised last, so after a torn-down connect the
            // SSL handler went into a dead pipeline.
            val pipeline = channel?.pipeline() ?: throw SocketGoneException()
            val sslContext = sharedClientSslContext()

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
        /**
         * One client TLS context for the whole process, built on first use.
         *
         * It was rebuilt for every upgrade, and a fresh context carries a fresh session cache, so
         * every reconnect paid for a full handshake. Reusing it lets the JDK resume the session
         * it already has for this host, which is the difference between two round trips and one.
         * The context is immutable and safe to share; only the per-channel handler is new.
         */
        @Volatile
        private var clientSslContext: SslContext? = null

        private val sslContextLock = Any()

        fun sharedClientSslContext(): SslContext =
            clientSslContext ?: synchronized(sslContextLock) {
                clientSslContext ?: SslContextBuilder.forClient().startTls(false).build()
                    .also { clientSslContext = it }
            }

        const val CONNECT_TIMEOUT_MS = 10_000L

        /** Ceiling on waiting for the TLS provider and the handshake together. */
        const val TLS_UPGRADE_TIMEOUT_MS = 15_000L

        /** Ceiling on how long a shut-down event loop group may take to actually stop. */
        const val GROUP_SHUTDOWN_TIMEOUT_MS = 2_000L
    }
}
