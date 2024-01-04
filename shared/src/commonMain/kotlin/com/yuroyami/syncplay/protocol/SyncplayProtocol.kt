package com.yuroyami.syncplay.protocol

import androidx.compose.runtime.mutableStateOf
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.obtainInt
import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.models.Session
import com.yuroyami.syncplay.protocol.JsonSender.sendHello
import com.yuroyami.syncplay.protocol.JsonSender.sendTLS
import com.yuroyami.syncplay.utils.loggy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class SyncplayProtocol {

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
    var state: Constants.CONNECTIONSTATE = Constants.CONNECTIONSTATE.STATE_DISCONNECTED
    var tls: Constants.TLS = Constants.TLS.TLS_NO

    /** Coroutine scopes and dispatchers */
    val protoScope = CoroutineScope(Dispatchers.IO)

    /** This method is responsible for bootstrapping (initializing) the Ktor TCP socket */
    open fun connect() {
        endConnection(false)

        /** Informing UI controllers that we are starting a connection attempt */
        syncplayCallback?.onConnectionAttempt()
        state = Constants.CONNECTIONSTATE.STATE_CONNECTING

        /** Bootstrapping our Ktor client  */
        protoScope.launch {
            try {
                connectSocket()

                loggy("PROTOCOL: Connected! HOST: ${session.serverHost}, PORT: ${session.serverPort}....", 202)

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
                if (isSocketValid()) {
                    val finalOut = json + "\r\n"
                    loggy("OUT: $finalOut", 206)
                    writeActualString(finalOut)
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

    /** Platform-specific (because we're using Netty on Android side, and plain Ktor on iOS side */
    abstract fun connectSocket()
    abstract fun isSocketValid(): Boolean
    abstract fun endConnection(terminating: Boolean)
    abstract fun writeActualString(s: String)
    abstract fun upgradeTls()
}