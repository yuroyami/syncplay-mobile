package app.protocol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.protocol.JsonHandler.handleJson
import app.protocol.JsonSender.sendHello
import app.wrappers.Constants.STATE_CONNECTED
import app.wrappers.Constants.STATE_CONNECTING
import app.wrappers.Constants.STATE_DISCONNECTED
import app.wrappers.Constants.STATE_SCHEDULING_RECONNECT
import app.wrappers.MediaFile
import app.wrappers.Session
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStream

open class SyncplayProtocol : ViewModel() {

    /** This refers to the event callback interface */
    var syncplayBroadcaster: ProtocolCallback? = null

    /** Protocol-exclusive variables - should never change these initial values **/
    var serverIgnFly: Int = 0
    var clientIgnFly: Int = 0
    var ping = 0.0
    var rewindThreshold = 12L /* This is as per official Syncplay, shouldn't be subject to change */


    /** Variables that track user status */
    var paused: Boolean = true
    var ready = false

    /** Variables related to current video properties */
    var file: MediaFile? = null
    var currentVideoPosition: Double = 0.0

    /** A protocol instance is always defined and accompanied by a session **/
    var session = Session()

    /** Our JSON instance */
    val gson: Gson = GsonBuilder().create()

    /** Late-initialized socket channel which will host all incoming and outcoming data **/
    var channel: Channel? = null
    var state: Int = STATE_DISCONNECTED
    var useTLS: Boolean = false

    /** Storing certificate without having to pass context **/
    lateinit var cert: InputStream

    /** ============================ start of protocol =====================================**/

    /** This method is responsible for bootstrapping (initializing) the Netty TCP socket client */
    fun connect() {
        /** Informing UI controllers that we are starting a connection attempt */
        syncplayBroadcaster?.onConnectionAttempt()
        state = STATE_CONNECTING

        /** Bootstrapping our Netty client. Bootstrapping basically means initializing and it has to be done once per connection. */
        viewModelScope.launch(Dispatchers.IO) {
            /** 1- Specifiying that we want the NIO event loop group. */
            val group: EventLoopGroup = NioEventLoopGroup()

            /** 2- Initializing a bootstrap instance */
            val b = Bootstrap()
            b.group(group) /* Assigning the event loop group to the bootstrap */
                .channel(NioSocketChannel::class.java) /* We want a NIO Socket Channel */
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        val p: ChannelPipeline = ch.pipeline() /* Getting the pipeline related to the channel */

                        /** All of the above repeats for every TCP client there is in the world. But below are parameters
                         * specific to our Android-to-SyncplayServer configuration. Most notably, the line delimiters.
                         *
                         * Syncplay servers run on Python's Twisted, which uses '\r\n' as line delimiters.
                         * Here, we tell Netty that it should divide every message when it encounters these delimiters.
                         *
                         * Note: This decoder should be added to the HEAD of the pipeline
                         */
                        p.addLast("framer", DelimiterBasedFrameDecoder(8192, *Delimiters.lineDelimiter()))

                        /** Telling our Netty that it should decode incoming bytestreams into strings */
                        p.addLast(StringDecoder())

                        /** Telling our Netty that it should encode any strings to bytestreams */
                        p.addLast(StringEncoder())

                        /** Assigning a reader handler to read incoming messages, should be added last to pipeline */
                        p.addLast(Reader())
                    }
                })

            /** After we're done bootstrapping Netty, now it's time to connect */
            val f: ChannelFuture = b.connect(session.serverHost, session.serverPort)

            /** Listening to the connection progress */
            f.addListener(ChannelFutureListener { future ->
                if (!future.isSuccess) {
                    syncplayBroadcaster?.onConnectionFailed()
                } else {
                    channel = f.channel() /* This is the channel, only variable we should memorize from the entire bootstrap/connection phase */
                    /** At this point, the connection should be made but we shouldn't as everything is chained and scheduled */
                    /** Now, we send a hello through the created channel */
                    sendPacket(sendHello(session.currentUsername, session.currentRoom, session.currentPassword))
                }
            })
            f.await(5000)

        }
    }

    /** WRITING: This small method basically checks if the channel is active and writes to it, otherwise
     *  it queues the json to send in a special queue until the connection recovers. */
    open fun sendPacket(json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (channel?.isActive == true) {
                val f: ChannelFuture? = channel?.writeAndFlush(json + "\r\n")
                f?.addListener(ChannelFutureListener { future ->
                    if (!future.isSuccess) {
                        syncplayBroadcaster?.onDisconnected()
                    }
                })
                f?.await(10000)
            } else {
                /** Queuing any pending outgoing messages */
                if (json != sendHello(session.currentUsername, session.currentRoom, session.currentPassword)) {
                    session.outboundQueue.add(json)
                }
                if (state == STATE_CONNECTED) syncplayBroadcaster?.onDisconnected()
            }
        }
    }

    /** READING: A small inner class that does the reading callback (Delegated by Netty) */
    inner class Reader : SimpleChannelInboundHandler<String>() {
        override fun channelRead0(ctx: ChannelHandlerContext?, msg: String?) {
            if (msg != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    handleJson(msg, this@SyncplayProtocol)
                }
            }
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
            super.exceptionCaught(ctx, cause)
            syncplayBroadcaster?.onDisconnected()
        }
    }

    /** This method schedules reconnection ONLY IN in disconnected state */
    fun reconnect() {
        if (state == STATE_DISCONNECTED) {
            viewModelScope.launch(Dispatchers.IO) {
                state = STATE_SCHEDULING_RECONNECT
                delay(3000)
                connect()
            }
        }
    }

    /** Binding function for the callback interface between the the protocol and activity */
    open fun setBroadcaster(broadcaster: ProtocolCallback?) {
        this.syncplayBroadcaster = broadcaster
    }

    companion object {

    }
}