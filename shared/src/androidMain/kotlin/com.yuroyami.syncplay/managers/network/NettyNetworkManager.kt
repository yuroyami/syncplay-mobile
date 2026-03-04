package com.yuroyami.syncplay.managers.network

import android.net.TrafficStats
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
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

/**
 * Netty-based implementation of NetworkManager for Android.
 *
 * Uses Netty's high-performance asynchronous networking framework to provide
 * TCP socket connectivity with TLS support. Netty is the default network engine
 * on Android due to its stability and efficiency.
 *
 * @property viewmodel The parent RoomViewModel that owns this manager
 */
class NettyNetworkManager(viewmodel: RoomViewmodel) : NetworkManager(viewmodel) {

    override val engine = NetworkEngine.NETTY

    /**
     * The active Netty channel for communication with the server.
     */
    private var channel: Channel? = null

    /**
     * The channel pipeline containing all handlers for processing data (in and out).
     * Used for inserting the SSL handler during TLS upgrade.
     */
    lateinit var pipeline: ChannelPipeline

    /**
     * Establishes a TCP connection to the Syncplay server using Netty.
     *
     * Bootstraps a Netty client with:
     * - NIO event loop group for async I/O
     * - String encoding/decoding handlers
     * - Line delimiter-based frame decoder (CRLF)
     * - Custom inbound handler for received packets
     *
     * Waits up to 10 seconds for connection to succeed, triggering
     * onConnectionFailed if timeout occurs.
     *
     * @throws Exception if connection fails (caught and triggers onConnectionFailed)
     */
    override suspend fun connectSocket() {
        val group: EventLoopGroup = NioEventLoopGroup()
        val b = Bootstrap()
        b.group(group) /* Assigning the event loop group to the bootstrap */
            .channel(NioSocketChannel::class.java) /* We want a NIO Socket Channel */
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    pipeline = ch.pipeline()
                    pipeline.addLast("framer", DelimiterBasedFrameDecoder(8192, *Delimiters.lineDelimiter()))
                    pipeline.addLast(StringDecoder())
                    pipeline.addLast(StringEncoder())
                    pipeline.addLast(object : SimpleChannelInboundHandler<String>() {
                        /**
                         * Logs channel lifecycle events for debugging.
                         */
                        override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
                            super.userEventTriggered(ctx, evt)
                            loggy("Channel event: ${evt.toString()}")
                        }

                        /**
                         * Processes incoming string messages (complete lines).
                         *
                         * @param ctx The channel handler context
                         * @param msg The received line of text (packet)
                         */
                        override fun channelRead0(ctx: ChannelHandlerContext?, msg: String?) {
                            if (msg != null) handlePacket(msg)
                        }

                        /**
                         * Handles exceptions on the channel.
                         *
                         * Logs the error and triggers disconnection callback.
                         *
                         * @param ctx The channel handler context
                         * @param cause The exception that occurred
                         */
                        @Deprecated("Deprecated in Java")
                        override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
                            super.exceptionCaught(ctx, cause)
                            loggy("EXCEPTION CAUGHT IN NETTY: ${cause?.stackTraceToString()}")
                            viewmodel.callbackManager.onDisconnected()
                        }
                    })
                }
            })

        /** After we're done bootstrapping Netty, now it's time to connect */
        TrafficStats.setThreadStatsTag(0xF00DFAF) //Satisfies Android's StrictMode policy

        val f = b.connect(
            viewmodel.sessionManager.session.serverHost,
            viewmodel.sessionManager.session.serverPort
        )
        val success = f.await(10000)
        if (!success) {
            viewmodel.callbackManager.onConnectionFailed()
        } else {
            /* This is the channel, only variable we should memorize from the entire bootstrap/connection phase */
            channel = f.channel()
            loggy("$channel")
        }
    }

    /**
     * Closes the Netty channel and cleans up resources.
     *
     * Waits for the close operation to complete before nullifying the channel reference.
     */
    override fun terminateExistingConnection() {
        try {
            loggy("Terminating network session.")
            channel?.close()?.await()
            channel = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Writes a string to the Netty channel.
     *
     * Flushes immediately and attaches a listener to detect write failures.
     * Triggers disconnection callback if the write operation fails.
     *
     * @param s The string to write to the channel
     */
    override suspend fun writeActualString(s: String) {
        val f = channel?.writeAndFlush(s)
        f?.addListener(ChannelFutureListener { future ->
            if (!future.isSuccess) {
                loggy("OH NO, no future...")
                viewmodel.callbackManager.onDisconnected()
            }
        })
    }

    /**
     * Indicates TLS support status.
     *
     * @return true - Netty supports TLS encryption
     */
    override fun supportsTLS() = true

    /**
     * Upgrades the connection to TLS by inserting an SSL handler into the pipeline.
     *
     * Creates an SSL context for client-side TLS, then inserts the SSL handler
     * at the beginning of the pipeline to encrypt all subsequent traffic.
     * The handler is configured for the specific server host and port.
     */
    override fun upgradeTls() {
        val sslContext = SslContextBuilder
            .forClient()
            //.sslProvider(SslProvider.JDK)
            //.trustManager(Conscrypt.getDefaultX509TrustManager())
            .startTls(false) //This isn't necessary for clients, we already do it manually
            .build()

        val h = sslContext.newHandler(
            pipeline.channel().alloc(),
            viewmodel.sessionManager.session.serverHost,
            viewmodel.sessionManager.session.serverPort
        )
        pipeline.addFirst(h)

    }

}