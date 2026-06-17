package app.protocol.network

import android.net.TrafficStats
import app.room.RoomViewmodel
import app.utils.loggy
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Netty-based [NetworkManager] for Android: async TCP socket with TLS support.
 * Default network engine on Android.
 */
class NettyNetworkManager(viewmodel: RoomViewmodel) : NetworkManager(viewmodel) {

    override val engine = NetworkEngine.NETTY

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
     * [handlePacket]. Waits up to 10s; on timeout calls onConnectionFailed.
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

                        @Deprecated("Deprecated in Java")
                        override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
                            super.exceptionCaught(ctx, cause)
                            loggy("EXCEPTION CAUGHT IN NETTY: ${cause?.stackTraceToString()}")
                            viewmodel.callback.onDisconnected()
                        }
                    })
                }
            })

        TrafficStats.setThreadStatsTag(0xF00DFAF) // Satisfies Android's StrictMode policy

        val f = b.connect(
            viewmodel.session.serverHost,
            viewmodel.session.serverPort
        )
        val success = f.await(10000)
        if (!success) {
            viewmodel.callback.onConnectionFailed()
        } else {
            channel = f.channel()
            loggy("$channel")
        }
    }

    /** Closes the channel and shuts down [group], releasing its NIO threads. */
    override fun terminateExistingConnection() {
        try {
            loggy("Terminating network session.")
            channel?.close()?.await()
            channel = null
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Release the NIO threads with the channel — see [group].
            group?.shutdownGracefully()
            group = null
        }
    }

    /** Writes and flushes [s]; a failed write triggers onDisconnected. */
    override suspend fun writeActualString(s: String) {
        val f = channel?.writeAndFlush(s)
        f?.addListener(ChannelFutureListener { future ->
            if (!future.isSuccess) {
                loggy("OH NO, no future...")
                viewmodel.callback.onDisconnected()
            }
        })
    }

    override fun supportsTLS() = true

    /**
     * Inserts an SSL handler at the front of the pipeline and suspends until the TLS handshake
     * completes (or fails). Awaiting the handshake guarantees a subsequent `Hello` write goes out
     * as ciphertext rather than relying on the SSL handler's buffering as a timing detail.
     */
    override suspend fun upgradeTls() = suspendCancellableCoroutine<Unit> { cont ->
        try {
            val sslContext = SslContextBuilder
                .forClient()
                .startTls(false)
                .build()

            val handler = sslContext.newHandler(
                pipeline.channel().alloc(),
                viewmodel.session.serverHost,
                viewmodel.session.serverPort
            )
            pipeline.addFirst(handler)
            handler.handshakeFuture().addListener { future ->
                if (future.isSuccess) cont.resume(Unit)
                else cont.resumeWithException(future.cause() ?: Exception("TLS handshake failed"))
            }
        } catch (e: Throwable) {
            cont.resumeWithException(e)
        }
    }

}