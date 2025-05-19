package com.yuroyami.syncplay.protocol

import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.models.Session
import com.yuroyami.syncplay.protocol.JsonSender.sendHello
import com.yuroyami.syncplay.protocol.JsonSender.sendTLS
import com.yuroyami.syncplay.protocol.parsing.JsonHandler
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.settings.valueSuspendingly
import com.yuroyami.syncplay.utils.PLATFORM
import com.yuroyami.syncplay.utils.getPlatform
import com.yuroyami.syncplay.utils.loggy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class SyncplayProtocol {
    /** This refers to the event callback interface */
    var syncplayCallback: ProtocolCallback? = null

    /** Protocol-exclusive variables - should never change these initial values **/
    var serverIgnFly: Int = 0
    var clientIgnFly: Int = 0
    var ping = MutableStateFlow<Int?>(null)
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
    val protoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** This method is responsible for bootstrapping (initializing) the Ktor TCP socket */
    open suspend fun connect() {
        endConnection(false)

        /** Informing UI controllers that we are starting a connection attempt */
        syncplayCallback?.onConnectionAttempt()
        state = Constants.CONNECTIONSTATE.STATE_CONNECTING

        /** Bootstrapping our Ktor client  */
        try {
            connectSocket()

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

    /** WRITING: This small method basically checks if the channel is active and writes to it, otherwise
     *  it queues the json to send in a special queue until the connection recovers. */
    open suspend fun sendPacket(json: String, isRetry: Boolean = false) {
        withContext(Dispatchers.IO) {
            try {
                if (isSocketValid()) {
                    val finalOut = json + "\r\n"
                    //loggy("Client: $finalOut", 206)
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

    fun handlePacket(data: String) {
        protoScope.launch {
            JsonHandler.parse(this@SyncplayProtocol, data)
        }
    }

    fun onError() {
        syncplayCallback?.onDisconnected()
    }

    fun terminateScope() {
        try {
            protoScope.cancel()
        } catch (_: Exception) {
        }
    }

    /** This method schedules reconnection ONLY IN in disconnected state */
    private var reconnectionJob: Job? = null
    fun reconnect() {
        if (state == Constants.CONNECTIONSTATE.STATE_DISCONNECTED) {
            if (reconnectionJob == null || reconnectionJob?.isCompleted == true) {
                reconnectionJob = protoScope.launch(Dispatchers.IO) {
                    state = Constants.CONNECTIONSTATE.STATE_SCHEDULING_RECONNECT
                    val reconnectionInterval = valueSuspendingly(DataStoreKeys.PREF_INROOM_RECONNECTION_INTERVAL, 2) * 1000L

                    delay(reconnectionInterval)

                    connect()
                }
            }
        }
    }

    /********************************************************************************************
     * Platform-specific (because we're using different engines on each platform)               *
     ********************************************************************************************/

    /** Attempts a connection to the host and port specified under [Session] */
    abstract suspend fun connectSocket()

    /** Whether the currently established socket is valid (active) */
    abstract fun isSocketValid(): Boolean

    /** Whether the currently selected network engine supports TLS */
    abstract fun supportsTLS(): Boolean

    /** Ends the connection and cancels any read/write operations. Disposes of any references */
    abstract fun endConnection(terminating: Boolean)

    /** Writes the string to the socket */
    abstract suspend fun writeActualString(s: String)

    /** Attempts to upgrade the plain TCP socket to a TLS secure socket */
    abstract fun upgradeTls()

    open val engine: NetworkEngine = NetworkEngine.SWIFTNIO

    enum class NetworkEngine {
        KTOR,
        NETTY,
        SWIFTNIO
    }

    companion object {
        fun getPreferredEngine(): NetworkEngine {
            val defaultEngine = if (getPlatform() == PLATFORM.Android) NetworkEngine.NETTY else NetworkEngine.SWIFTNIO
            val engineName = valueBlockingly(DataStoreKeys.PREF_NETWORK_ENGINE, defaultEngine.name.lowercase())
            return NetworkEngine.valueOf(engineName.uppercase())
        }
    }
}
