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
 * Netty-based [NetworkManager] for the JVM platforms: an async TCP socket with TLS support. A
 * network manager carries the Syncplay protocol lines between the room and the server. Netty is
 * the default network engine on Android and desktop.
 *
 * Android and desktop share this one file, so a Netty fix lands once. Their two differences,
 * socket tagging and the wait for Conscrypt, sit behind [tagSocketThread] and
 * [awaitTlsProviderReady].
 */
class NettyNetworkManager(viewmodel: RoomViewmodel) : NetworkManager(viewmodel) {

    override val engine = NetworkEngine.NETTY

    @Volatile
    private var channel: Channel? = null

    /**
     * The event loop group behind [channel]. Shut it down together with the channel: each group
     * owns its NIO threads, so a group released later than its channel leaks those threads for
     * the life of the process.
     *
     * Volatile for the same reason as [channel]: the connect coroutine writes it, and
     * [terminateExistingConnection] reads it. The channel watchdog and the handshake deadline
     * both call that function from their own coroutines.
     */
    @Volatile
    private var group: EventLoopGroup? = null

    /**
     * Opens a TCP connection to the Syncplay server. Bootstraps a NIO client with string codecs,
     * a line-frame decoder, and an inbound handler that passes each line to [handlePacket]. Waits
     * up to 10 s. A refused, unreachable or timed-out connect throws, and [connect] turns that
     * into onConnectionFailed.
     */
    override suspend fun connectSocket() {
        loggy("Handshake: entering the transport after ${sinceHandshakeStart()}")
        // One thread, explicitly. The no-argument constructor sizes the group at twice the core
        // count, which on a phone starts sixteen NIO threads for the one socket that this client
        // opens, and does it again on every reconnect attempt.
        val group: EventLoopGroup = NioEventLoopGroup(1)
        this.group = group
        val b = Bootstrap()
        b.group(group)
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val pipeline = ch.pipeline()
                    // 64 KiB line cap, the same as the hosted server's framer. It must stay large
                    // enough for a large List response (a big room plus a playlist near the
                    // protocol's 10000-char limit). A smaller cap overflows the decoder and causes
                    // a reconnect loop.
                    pipeline.addLast("framer", DelimiterBasedFrameDecoder(65536, *Delimiters.lineDelimiter()))
                    pipeline.addLast(StringDecoder())
                    pipeline.addLast(StringEncoder())
                    pipeline.addLast(object : SimpleChannelInboundHandler<String>() {
                        override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
                            super.userEventTriggered(ctx, evt)
                            loggy("Channel event: ${evt.toString()}")
                        }

                        override fun channelRead0(ctx: ChannelHandlerContext?, msg: String?) {
                            // Ignore a line from a socket that a newer connection has replaced.
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
            /* A failed dial leaves this group with nothing to serve, so shut it down now instead
             * of letting it hold its NIO thread until the next connect attempt. Shut it down even
             * when a newer attempt has already taken the field: then nothing else owns this group,
             * and it would never be released. */
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
                    // A completed future is not always a success: a refused or unreachable host
                    // completes it with a cause.
                    if (future.isSuccess) cont.resume(f.channel())
                    else cont.resumeWithException(future.cause() ?: IOException("Connect failed"))
                }
                cont.invokeOnCancellation {
                    // If the cancel loses the race, the connect already succeeded. Cancelling the
                    // future then does nothing, and the socket stays open with nothing holding it
                    // (`channel` is assigned only after this block returns). So close it here.
                    if (!f.cancel(true)) f.channel()?.close()
                }
            }
        }

    /**
     * Handles a socket that closed from the other side. Only the current channel counts: closing
     * a previous socket ourselves is expected. In CONNECTING, the close means the server ended
     * the handshake (for example on a wrong password), so the room gets onConnectionFailed
     * instead of waiting with no callback.
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
            /* Release the NIO threads with the channel (see [group]). No quiet period: the
             * channel is already closed above, so there is nothing to wind down. The default
             * two-second quiet period is longer than the shortest reconnect interval, so each
             * retry would run its group beside the one before it. */
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
            // The caller writes under a timeout. Without this cancel, the timeout only abandons
            // the wait: the write stays queued and still goes out, and the caller's retry sends
            // the same line a second time.
            cont.invokeOnCancellation { f.cancel(false) }
        }
    }

    override fun supportsTLS() = true

    /**
     * Inserts an SSL handler at the front of the pipeline and suspends until the TLS handshake
     * completes or fails. Waiting for the handshake makes sure that the next `Hello` write goes
     * out encrypted, instead of relying on the SSL handler's buffering as a timing detail.
     *
     * The certificate is checked against the host name that the user typed (SNI carries it too),
     * not the IP that the socket dialled. Without that check, any certificate from anyone on the
     * network path passes, and the encryption protects nothing.
     */
    override suspend fun upgradeTls() {
        /* The upgrade has a time limit, like the handshake on the SwiftNIO (iOS) side. This runs
         * on the serial inbound consumer, so a handshake that never finishes stops every packet
         * behind it. The channel close from the handshake deadline does fail the promise in the
         * end, but the wait for the TLS provider happens before any handler is in the pipeline,
         * where a channel close cannot reach it. Against the official server, the whole upgrade
         * takes about a second.
         *
         * The timeout leaves as an ordinary IOException. TimeoutCancellationException is a
         * CancellationException, and the caller rethrows those by contract. That would stop the
         * inbound consumer and leave the connection unable to read anything again. */
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
            // Take the pipeline from the current channel, not from a field that the
            // ChannelInitializer writes. Such a field tracks whichever channel was initialised
            // last, so after a torn-down connect the SSL handler goes into a dead pipeline.
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
                if (future.isSuccess) {
                    tlsVersion = runCatching { handler.engine().session.protocol }.getOrNull()
                    cont.resume(Unit)
                } else {
                    cont.resumeWithException(future.cause() ?: Exception("TLS handshake failed"))
                }
            }
        } catch (e: Throwable) {
            cont.resumeWithException(e)
        }
    }

    private companion object {
        /**
         * One client TLS context for the whole process, built on first use.
         *
         * A fresh context has a fresh session cache, so a context built per upgrade makes every
         * reconnect pay for a full handshake. Reuse lets the JDK resume the session that it
         * already has for this host: one round trip instead of two. The context is immutable and
         * safe to share; only the per-channel handler is new.
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

        /** The time limit for the TLS provider wait and the handshake together. */
        const val TLS_UPGRADE_TIMEOUT_MS = 15_000L

        /** The longest time that a shut-down event loop group may take to stop. */
        const val GROUP_SHUTDOWN_TIMEOUT_MS = 2_000L
    }
}
