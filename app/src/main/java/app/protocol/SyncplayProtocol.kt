package app.protocol

import android.util.Log
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.datastore.DataStoreKeys
import app.datastore.DataStoreKeys.DATASTORE_INROOM_PREFERENCES
import app.datastore.DataStoreUtils.obtainInt
import app.protocol.JsonHandler.handleJson
import app.protocol.JsonSender.sendHello
import app.protocol.JsonSender.sendTLS
import app.wrappers.Constants
import app.wrappers.Constants.CONNECTIONSTATE.*
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
import io.netty.handler.ssl.SslContextBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import javax.net.ssl.TrustManagerFactory


open class SyncplayProtocol : ViewModel() {

    /** This refers to the event callback interface */
    var syncplayBroadcaster: ProtocolCallback? = null

    /** Protocol-exclusive variables - should never change these initial values **/
    var serverIgnFly: Int = 0
    var clientIgnFly: Int = 0
    var ping = mutableDoubleStateOf(-1.0)
    val rewindThreshold = 12L /* This is as per official Syncplay, shouldn't be subject to change */

    /** Variables that track user status */
    var paused: Boolean = true
    var ready = false

    /** Variables related to current video properties which will be fed to Syncplay Server for syncing */
    var currentVideoPosition: Double = 0.0

    /** A protocol instance is always defined and accompanied by a session **/
    var session = Session()

    /** Our JSON instance */
    val gson: Gson = GsonBuilder().create()

    /** Late-initialized socket channel which will host all incoming and outcoming data **/
    var channel: Channel? = null
    var state: Constants.CONNECTIONSTATE = Constants.CONNECTIONSTATE.STATE_DISCONNECTED
    var tls: Constants.TLS = Constants.TLS.TLS_NO

    /** Storing certificate without having to pass context **/
    lateinit var cert: InputStream

    /** ============================ start of protocol =====================================**/

    /** This method is responsible for bootstrapping (initializing) the Netty TCP socket client
     *
     * @param tlsMode Indicates how the client will behave on first initialization.
     * Using 'null' will set up */
    fun connect() {
        /** Informing UI controllers that we are starting a connection attempt */
        syncplayBroadcaster?.onConnectionAttempt()
        state = STATE_CONNECTING

        /** Bootstrapping our Netty client. Bootstrapping basically means initializing
         * and it has to be done once per connection. */
        viewModelScope.launch(Dispatchers.IO) {
            /** 1- Specifiying that we want the NIO event loop group. */
            val group: EventLoopGroup = NioEventLoopGroup()

            /** 2- Initializing a bootstrap instance */
            val b = Bootstrap()
            b.group(group) /* Assigning the event loop group to the bootstrap */
                .channel(NioSocketChannel::class.java) /* We want a NIO Socket Channel */
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        val p: ChannelPipeline =
                            ch.pipeline() /* Getting the pipeline related to the channel */

                        /** Should we establish a TLS connection ? */
                        if (tls == Constants.TLS.TLS_YES) {
                            Log.e("TLS", "Attempting TLS")
                            val c = SslContextBuilder
                                .forClient()
                                .trustManager(TrustManagerFactory.getInstance("TLSv1.2"))
                                .build()
                            val h = c.newHandler(ch.alloc(), session.serverHost, session.serverPort)
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
            val f: ChannelFuture = b.connect(session.serverHost, session.serverPort)

            /** Listening to the connection progress */
            f.addListener(ChannelFutureListener { future ->
                if (!future.isSuccess) {
                    syncplayBroadcaster?.onConnectionFailed()
                } else {
                    /* This is the channel, only variable we should memorize from the entire bootstrap/connection phase */
                    channel = f.channel()


                    /** if the TLS mode is [Constants.TLS.TLS_ASK], then the the first packet to send
                     * concerns an opportunistic TLS check with the server, otherwise, a Hello would be first */
                    if (tls == Constants.TLS.TLS_ASK) {
                        sendPacket(sendTLS())
                    } else {
                        sendPacket(
                            sendHello(
                                session.currentUsername,
                                session.currentRoom,
                                session.currentPassword
                            )
                        )
                    }
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
                if (json != sendHello(
                        session.currentUsername,
                        session.currentRoom,
                        session.currentPassword
                    )
                ) {
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
                    Log.e("zrf", msg)
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
                val reconnectionInterval =
                    runBlocking { DATASTORE_INROOM_PREFERENCES.obtainInt(DataStoreKeys.PREF_INROOM_RECONNECTION_INTERVAL, 2) } * 1000
                delay(reconnectionInterval.toLong())
                connect()
            }
        }
    }

    /** Binding function for the callback interface between the the protocol and activity */
    open fun setBroadcaster(broadcaster: ProtocolCallback?) {
        this.syncplayBroadcaster = broadcaster
    }
}