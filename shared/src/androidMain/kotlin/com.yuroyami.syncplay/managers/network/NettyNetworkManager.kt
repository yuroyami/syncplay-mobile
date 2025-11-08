package com.yuroyami.syncplay.managers.network

import android.net.TrafficStats
import com.yuroyami.syncplay.managers.network.NetworkManager
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

class NettyNetworkManager(viewmodel: RoomViewmodel) : NetworkManager(viewmodel) {

    override val engine = NetworkEngine.NETTY

    /** Netty stuff */
    private var channel: Channel? = null
    lateinit var pipeline: ChannelPipeline

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

    override fun terminateExistingConnection() {
        try {
            loggy("Terminating network session.")
            channel?.close()?.await()
            channel = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun writeActualString(s: String) {
//        loggy("CHANNEL ${channel}")
//        loggy("WRITABLE ${channel?.isWritable}")
//        loggy("ACTIVE ${channel?.isActive}")
//        loggy("OPEN ${channel?.isOpen}")
//        loggy("REGISTERED ${channel?.isRegistered}")

        val f = channel?.writeAndFlush(s)
        f?.addListener(ChannelFutureListener { future ->
            if (!future.isSuccess) {
                loggy("OH NO, no future...")
                viewmodel.callbackManager.onDisconnected()
            }
        })
    }

    override fun supportsTLS() = true

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