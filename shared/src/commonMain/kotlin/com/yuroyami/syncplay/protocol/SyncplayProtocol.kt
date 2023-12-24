package com.yuroyami.syncplay.protocol

import androidx.compose.runtime.mutableStateOf
import com.yuroyami.syncplay.datastore.DataStoreKeys
import com.yuroyami.syncplay.datastore.obtainInt
import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.models.Session
import com.yuroyami.syncplay.protocol.JsonHandler.handleJson
import com.yuroyami.syncplay.protocol.JsonSender.sendHello
import com.yuroyami.syncplay.protocol.JsonSender.sendTLS
import com.yuroyami.syncplay.utils.loggy
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.network.sockets.isClosed
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

open class SyncplayProtocol {

    /** This refers to the event callback interface */
    var syncplayCallback: ProtocolCallback? = null

    /** Protocol-exclusive variables - should never change these initial values **/
    var serverIgnFly: Int = 0
    var clientIgnFly: Int = 0
    var ping = mutableStateOf<Int?>(null)
    val rewindThreshold = 12L /* This is as per official Syncplay, shouldn't be subject to change */

    /** Variables that track user status */
    var paused: Boolean = true
    var ready = false

    /** Variables related to current video properties which will be fed to Syncplay Server for syncing */
    var currentVideoPosition: Double = 0.0

    /** A protocol instance is always defined and accompanied by a session **/
    var session = Session()

    /** Late-initialized socket channel which will host all incoming and outcoming data **/
    var socket: Socket? = null
    var connection: Connection? = null
    var state: Constants.CONNECTIONSTATE = Constants.CONNECTIONSTATE.STATE_DISCONNECTED
    var tls: Constants.TLS = Constants.TLS.TLS_NO

    /** Coroutine scopes and dispatchers */
    private val protoScope = CoroutineScope(Dispatchers.IO)

    /** ============================ start of protocol =====================================**/

    fun endConnection(terminating: Boolean) {
        try {
            /* Cleaning leftovers */
            socket?.close()
            socket?.dispose()

            if (terminating) {
                protoScope.cancel("")
            }
        } catch (_: Exception) {
        }
    }
    /** This method is responsible for bootstrapping (initializing) the Ktor TCP socket */
    fun connect() {
        endConnection(false)

        /** Informing UI controllers that we are starting a connection attempt */
        syncplayCallback?.onConnectionAttempt()
        state = Constants.CONNECTIONSTATE.STATE_CONNECTING

        /** Bootstrapping our Ktor client  */
        protoScope.launch {

            /** Should we establish a TLS connection ? */
            if (tls == Constants.TLS.TLS_YES) {
                loggy("Attempting TLS", 201)
            }

            try {
                socket = aSocket(SelectorManager(Dispatchers.IO))
                    .tcp()
                    .connect(InetSocketAddress(session.serverHost, session.serverPort))
                    .run {
                        if (tls == Constants.TLS.TLS_YES) {
                            this //tls(this.coroutineContext)
                        } else this
                    }

                loggy("PROTOCOL: Attempting to connect. HOST: ${session.serverHost}, PORT: ${session.serverPort}....", 202)

                connection = socket?.connection()

                /** Initiate reading */
                launch {
                    while (true) {
                        try {
                            connection?.input?.awaitContent()
                            connection?.input?.readUTF8Line()?.let { ln ->
                                loggy(ln, 203)
                                launch {
                                    handleJson(json = ln)
                                }
                            }
                        } catch (e: CancellationException) {
                            loggy(e.stackTraceToString(), 204)
                            throw e
                        } catch (e2: Exception) {
                            loggy(e2.stackTraceToString(), 2040)
                            onError()
                        }
                    }
                }

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
            } catch (e: Exception) {
                loggy(e.stackTraceToString(), 205)
                syncplayCallback?.onConnectionFailed()
            }
        }
    }

    /** WRITING: This small method basically checks if the channel is active and writes to it, otherwise
     *  it queues the json to send in a special queue until the connection recovers. */
    open fun sendPacket(json: String, isRetry: Boolean = false) {
        protoScope.launch {
            try {
                if (socket != null && socket?.isClosed == false) {
                    val finalOut = json + "\r\n"

                    loggy("OUT: $finalOut", 206)

                    connection?.output?.writeStringUtf8(finalOut)
                    connection?.output?.flush()
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
                    if (state == Constants.CONNECTIONSTATE.STATE_CONNECTED) {
                        onError()
                    }
                }
            } catch (e: Exception) {
                loggy(e.stackTraceToString(), 207)
                if (isRetry) {
                    onError()
                } else {
                    sendPacket(json, isRetry = true)
                }
            }
        }
    }

    private fun onError() {
        syncplayCallback?.onDisconnected()
    }

    /** This method schedules reconnection ONLY IN in disconnected state */

    private var reconnectionJob: Job? = null
    fun reconnect() {
        if (state == Constants.CONNECTIONSTATE.STATE_DISCONNECTED) {
            if (reconnectionJob == null || reconnectionJob?.isCompleted == true) {
                reconnectionJob = protoScope.launch(Dispatchers.IO) {
                    state = Constants.CONNECTIONSTATE.STATE_SCHEDULING_RECONNECT
                    val reconnectionInterval = obtainInt(DataStoreKeys.PREF_INROOM_RECONNECTION_INTERVAL, 2) * 1000L

                    delay(reconnectionInterval)

                    connect()
                }
            }
        }
    }
}