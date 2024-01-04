package com.yuroyami.syncplay.protocol

import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.protocol.JsonHandler.handleJson
import com.yuroyami.syncplay.utils.loggy
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.conscrypt.Conscrypt

class SpProtocolAndroid : SyncplayProtocol() {

    /** Netty stuff */
    var channel: Channel? = null

    override fun connectSocket() {
        val sslContext = SslContextBuilder
            .forClient()
            .trustManager(Conscrypt.getDefaultX509TrustManager())
            .startTls(true)
            .build()

        /** 1- Specifiying that we want the NIO event loop group. */
        val group: EventLoopGroup = NioEventLoopGroup()

        /** 2- Initializing a bootstrap instance */
        val b = Bootstrap()
        b.group(group) /* Assigning the event loop group to the bootstrap */
            .channel(NioSocketChannel::class.java) /* We want a NIO Socket Channel */
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val p: ChannelPipeline = ch.pipeline()

                    /** Should we establish a TLS connection ? */
                    if (tls == Constants.TLS.TLS_ASK) {
                        val h = sslContext.newHandler(ch.alloc(), session.serverHost, session.serverPort)
                        p.addLast(h)

                        //val engine: SSLEngine? = SSLContext.getDefault().createSSLEngine()
                        //engine?.useClientMode = true
                        //p.addLast("ssl", SslHandler(engine))
                    }

                    /** We should never forget \r\n delimiters, or we would get no input */
                    p.addLast(
                        "framer",
                        DelimiterBasedFrameDecoder(8192, *Delimiters.lineDelimiter())
                    )

                    /** Telling our Netty that it should decode incoming bytestreams into strings */
                    p.addLast(StringDecoder())

                    /** Telling our Netty that it should encode any strings to bytestreams */
                    p.addLast(StringEncoder())

                    /** Assigning a reader handler to read incoming messages, should be added last to pipeline */
                    p.addLast(Reader())
                }
            })

        /** After we're done bootstrapping Netty, now it's time to connect */
        val f = b.connect(session.serverHost, session.serverPort)

        /** Listening to the connection progress */
        f.addListener(ChannelFutureListener { future ->
            if (!future.isSuccess) {
                syncplayCallback?.onConnectionFailed()
            } else {
                /* This is the channel, only variable we should memorize from the entire bootstrap/connection phase */
                channel = f.channel()
            }
        })

        if (!f.await(10000)) throw Exception()
    }

    override fun isSocketValid() = channel?.isActive == true

    override fun endConnection(terminating: Boolean) {
        try {
            /* Cleaning leftovers */
            channel?.close()

            if (terminating) {
                protoScope.cancel("")
            }
        } catch (_: Exception) {
        }
    }

    override fun writeActualString(s: String) {
        val f = channel?.writeAndFlush(s)
        f?.addListener(ChannelFutureListener { future ->
            if (!future.isSuccess) {
                syncplayCallback?.onDisconnected()
            }
        })
        f?.await(10000)
    }


    /** NETTY READING: A small inner class that does the reading callback (Delegated by Netty) */
    inner class Reader : SimpleChannelInboundHandler<String>() {
        override fun channelRead0(ctx: ChannelHandlerContext?, msg: String?) {
            if (msg != null) {
                protoScope.launch {
                    loggy(msg, 2002)
                    handleJson(msg)
                }
            }
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
            super.exceptionCaught(ctx, cause)
            syncplayCallback?.onDisconnected()
        }
    }


}