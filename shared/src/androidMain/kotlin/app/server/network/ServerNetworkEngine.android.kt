package app.server.network

import android.net.TrafficStats
import app.server.ClientConnection
import app.server.SyncplayServer
import app.utils.loggy
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel as NettyChannel
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

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
    private var serverChannel: NettyChannel? = null
    // Concurrent: Netty delivers channelActive/channelInactive/exceptionCaught for different
    // channels on multiple event-loop threads, so this per-channel registry must be thread-safe.
    private val clientChannels = ConcurrentHashMap<NettyChannel, ClientMailbox>()

    /** A client's handler and the ordered queue its lines wait in. */
    private class ClientMailbox(val connection: ClientConnection, val mailbox: Channel<String>)

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
                            // One mailbox and one consumer per socket: lines are handled in arrival
                            // order, and the connection is only reported lost after the last line it
                            // sent was handled. Fanning each line onto a pool let a later line, or the
                            // loss itself, overtake the Hello and leave a ghost watcher behind.
                            val mailbox = Channel<String>(Channel.UNLIMITED)
                            clientChannels[ctx.channel()] = ClientMailbox(connection, mailbox)
                            scope.launch(Dispatchers.Default) {
                                try {
                                    for (line in mailbox) connection.handlePacket(line)
                                } finally {
                                    connection.onConnectionLost()
                                }
                            }
                            loggy("Server: Client connected from ${ctx.channel().remoteAddress()}")
                        }

                        override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
                            clientChannels[ctx.channel()]?.mailbox?.trySend(msg)
                        }

                        override fun channelInactive(ctx: ChannelHandlerContext) {
                            clientChannels.remove(ctx.channel())?.mailbox?.close()
                            loggy("Server: Client disconnected from ${ctx.channel().remoteAddress()}")
                        }

                        @Deprecated("Deprecated in Java")
                        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                            loggy("Server: Exception from ${ctx.channel().remoteAddress()}: ${cause.message}")
                            clientChannels.remove(ctx.channel())?.mailbox?.close()
                            ctx.close()
                        }
                    })
                }
            })

        try {
            val future = bootstrap.bind(port).sync()
            serverChannel = future.channel()
        } catch (e: Exception) {
            // A port in use used to leak both event-loop groups on every retry.
            workerGroup?.shutdownGracefully()
            bossGroup?.shutdownGracefully()
            workerGroup = null
            bossGroup = null
            throw e
        }
        isRunning = true
        loggy("Server: Listening on port $port")
    }

    actual fun stop() {
        isRunning = false

        for ((channel, client) in clientChannels.toMap()) {
            client.mailbox.close()
            channel.close()
        }
        clientChannels.clear()

        serverChannel?.close()?.sync()
        serverChannel = null

        workerGroup?.shutdownGracefully()
        bossGroup?.shutdownGracefully()
        workerGroup = null
        bossGroup = null

        loggy("Server: Stopped")
    }
}
