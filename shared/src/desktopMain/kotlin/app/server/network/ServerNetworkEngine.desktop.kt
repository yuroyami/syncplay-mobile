package app.server.network

import app.server.ClientConnection
import app.server.SyncplayServer
import app.utils.loggy
import io.netty.bootstrap.ServerBootstrap
import java.util.concurrent.TimeUnit
import io.netty.channel.Channel as NettyChannel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.WriteBufferWaterMark
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
 * Desktop TCP server engine for the hosted Syncplay server, built on Netty. It matches the
 * Android engine, except for Android's socket tagging.
 *
 * Netty's event-loop threads only deliver decoded lines. Protocol parsing and dispatch run in
 * [scope] coroutines, so the IO threads stay free.
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
        bossGroup = NioEventLoopGroup(1)
        // Two threads, not Netty's default of twice the core count. This server hosts a few
        // friends, and every extra loop is a thread that lives as long as the server.
        workerGroup = NioEventLoopGroup(2)

        val bootstrap = ServerBootstrap()
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, ACCEPT_BACKLOG)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            /* The watermark gives isWritable a meaning. Without it, output for a client that
             * stops reading only piles up: the server writes a State every second plus every
             * broadcast, and the heap grows for as long as that client's socket stays open. */
            .childOption(
                ChannelOption.WRITE_BUFFER_WATER_MARK,
                WriteBufferWaterMark(WRITE_WATERMARK_LOW, WRITE_WATERMARK_HIGH),
            )
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
                                    val ch = ctx.channel()
                                    if (ch.isWritable) {
                                        ch.writeAndFlush(line + "\r\n")
                                    } else {
                                        /* Past the high watermark, this client has a quarter of
                                         * a megabyte of unread lines queued, about a thousand
                                         * State messages. It is not slow, it is gone, and more
                                         * writes only cost the host memory. */
                                        loggy("Server: dropping ${ch.remoteAddress()}, outbound buffer full")
                                        ch.close()
                                    }
                                },
                                dropFn = {
                                    ctx.channel().close()
                                }
                            )
                            // One mailbox and one consumer per socket: lines are handled in arrival
                            // order, and the connection is reported lost only after its last line
                            // was handled. Do not hand each line to a pool: a later line, or the
                            // loss itself, can then overtake the Hello and leave a ghost watcher (a
                            // user who stays in the room list after leaving).
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
            // Shut both event-loop groups down, or a port in use leaks them on every retry.
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

        /* No quiet period: the listening channel and every client channel are already closed
         * above, so there is nothing left to wind down. The default two-second quiet period keeps
         * the old event loops alive after stop() returns, and a restart on the same port then
         * runs a second pair of groups beside them. */
        workerGroup?.shutdownGracefully(0L, GROUP_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        bossGroup?.shutdownGracefully(0L, GROUP_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        workerGroup = null
        bossGroup = null

        loggy("Server: Stopped")
    }

    private companion object {
        /** Pending connections the OS may hold before the accept loop reaches them. */
        const val ACCEPT_BACKLOG = 64

        /** Netty reports the channel writable again once the queue falls back to this. */
        const val WRITE_WATERMARK_LOW = 32 * 1024

        /** Above this many unflushed bytes for one client, the client is not reading. */
        const val WRITE_WATERMARK_HIGH = 256 * 1024

        /** The longest time that a shut-down event loop group may take to stop. */
        const val GROUP_SHUTDOWN_TIMEOUT_SECONDS = 2L
    }
}
