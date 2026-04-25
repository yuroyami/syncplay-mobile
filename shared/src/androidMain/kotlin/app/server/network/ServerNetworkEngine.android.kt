package app.server.network

import android.net.TrafficStats
import app.server.ClientConnection
import app.server.SyncplayServer
import app.utils.loggy
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.DelimiterBasedFrameDecoder
import io.netty.handler.codec.Delimiters
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

/**
 * Android Netty-based TCP server engine.
 *
 * Netty's event-loop threads only deliver decoded lines — actual protocol parsing and
 * dispatch run on [scope] coroutines so the IO threads stay free.
 */
actual class ServerNetworkEngine actual constructor(
    private val server: SyncplayServer,
    private val scope: CoroutineScope
) {
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var serverChannel: Channel? = null
    private val clientChannels = mutableMapOf<Channel, ClientConnection>()

    var isRunning: Boolean = false
        private set

    actual suspend fun startListening(port: Int) {
        TrafficStats.setThreadStatsTag(0xF00DFAF)

        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()

        val bootstrap = ServerBootstrap()
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val pipeline = ch.pipeline()
                    pipeline.addLast("framer", DelimiterBasedFrameDecoder(65536, *Delimiters.lineDelimiter()))
                    pipeline.addLast("decoder", StringDecoder(StandardCharsets.UTF_8))
                    pipeline.addLast("encoder", StringEncoder(StandardCharsets.UTF_8))
                    pipeline.addLast("handler", object : SimpleChannelInboundHandler<String>() {

                        override fun channelActive(ctx: ChannelHandlerContext) {
                            val connection = ClientConnection(
                                server = server,
                                sendFn = { line ->
                                    ctx.channel().writeAndFlush(line + "\r\n")
                                },
                                dropFn = {
                                    ctx.channel().close()
                                }
                            )
                            clientChannels[ctx.channel()] = connection
                            loggy("Server: Client connected from ${ctx.channel().remoteAddress()}")
                        }

                        override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
                            val connection = clientChannels[ctx.channel()] ?: return
                            scope.launch(Dispatchers.Default) {
                                connection.handlePacket(msg)
                            }
                        }

                        override fun channelInactive(ctx: ChannelHandlerContext) {
                            val connection = clientChannels.remove(ctx.channel())
                            connection?.onConnectionLost()
                            loggy("Server: Client disconnected from ${ctx.channel().remoteAddress()}")
                        }

                        @Deprecated("Deprecated in Java")
                        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                            loggy("Server: Exception from ${ctx.channel().remoteAddress()}: ${cause.message}")
                            val connection = clientChannels.remove(ctx.channel())
                            connection?.onConnectionLost()
                            ctx.close()
                        }
                    })
                }
            })

        val future = bootstrap.bind(port).sync()
        serverChannel = future.channel()
        isRunning = true
        loggy("Server: Listening on port $port")
    }

    actual fun stop() {
        isRunning = false

        // Close all client connections
        for ((channel, connection) in clientChannels.toMap()) {
            connection.onConnectionLost()
            channel.close()
        }
        clientChannels.clear()

        // Shutdown server
        serverChannel?.close()?.sync()
        serverChannel = null

        workerGroup?.shutdownGracefully()
        bossGroup?.shutdownGracefully()
        workerGroup = null
        bossGroup = null

        loggy("Server: Stopped")
    }
}
