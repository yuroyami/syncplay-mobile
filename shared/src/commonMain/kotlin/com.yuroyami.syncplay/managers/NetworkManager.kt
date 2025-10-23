package com.yuroyami.syncplay.managers

import com.yuroyami.syncplay.logic.AbstractManager
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import com.yuroyami.syncplay.logic.datastore.DataStoreKeys
import com.yuroyami.syncplay.logic.datastore.valueBlockingly
import com.yuroyami.syncplay.logic.datastore.valueSuspendingly
import com.yuroyami.syncplay.logic.protocol.PacketCreator
import com.yuroyami.syncplay.managers.ProtocolManager.Companion.createPacketInstance
import com.yuroyami.syncplay.managers.SessionManager.Session
import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.utils.PLATFORM
import com.yuroyami.syncplay.utils.ProtocolDsl
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

abstract class NetworkManager(viewmodel: SyncplayViewmodel) : AbstractManager(viewmodel) {

    open val engine: NetworkEngine = NetworkEngine.SWIFTNIO

    val networkJob = SupervisorJob()
    val networkScope = CoroutineScope(Dispatchers.IO + networkJob)

    var state: Constants.CONNECTIONSTATE = Constants.CONNECTIONSTATE.STATE_DISCONNECTED
    var tls: Constants.TLS = Constants.TLS.TLS_NO

    enum class NetworkEngine {
        KTOR, NETTY, SWIFTNIO
    }

    companion object {
        fun getPreferredEngine(): NetworkEngine {
            val defaultEngine = if (platform == PLATFORM.Android) NetworkEngine.NETTY else NetworkEngine.SWIFTNIO
            val engineName = valueBlockingly(DataStoreKeys.PREF_NETWORK_ENGINE, defaultEngine.name.lowercase())
            return NetworkEngine.valueOf(engineName.uppercase())
        }
    }

    /** This method is responsible for bootstrapping (initializing) the Ktor TCP socket */
    open suspend fun connect() {
        endConnection(false)

        /** Informing UI controllers that we are starting a connection attempt */
        viewmodel.callbackManager.onConnectionAttempt()
        state = Constants.CONNECTIONSTATE.STATE_CONNECTING

        /** Bootstrapping our Ktor client  */
        try {
            connectSocket()

            /** if the TLS mode is [Constants.TLS.TLS_ASK], then the the first packet to send
             * concerns an opportunistic TLS check with the server, otherwise, a Hello would be first */
            if (tls == Constants.TLS.TLS_ASK) {
                send<PacketCreator.TLS>().await()
            } else {
                send<PacketCreator.Hello> {
                    username = viewmodel.sessionManager.session.currentUsername
                    roomname = viewmodel.sessionManager.session.currentRoom
                    serverPassword = viewmodel.sessionManager.session.currentPassword
                }.await()
            }
        } catch (e: Exception) {
            loggy(e.stackTraceToString())
            viewmodel.callbackManager.onConnectionFailed()
        }
    }

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

    /** This method schedules reconnection ONLY IN in disconnected state */
    private var reconnectionJob: Job? = null
    fun reconnect() {
        if (state == Constants.CONNECTIONSTATE.STATE_DISCONNECTED) {
            if (reconnectionJob == null || reconnectionJob?.isCompleted == true) {
                reconnectionJob = networkScope.launch {
                    state = Constants.CONNECTIONSTATE.STATE_SCHEDULING_RECONNECT
                    val reconnectionInterval = valueSuspendingly(DataStoreKeys.PREF_INROOM_RECONNECTION_INTERVAL, 2) * 1000L

                    delay(reconnectionInterval)

                    connect()
                }
            }
        }
    }

    fun handlePacket(data: String) {
        networkScope.launch {
            viewmodel.protocolManager.packetHandler.parse(data)
        }
    }

    private fun onError() {
       viewmodel.callbackManager.onDisconnected()
    }

    fun terminateScope() {
        runCatching {
            networkScope.cancel()
        }
    }

    /** WRITING: This small method basically checks if the channel is active and writes to it, otherwise
     *  it queues the json to send in a special queue until the connection recovers. */
    typealias SendablePacket = String

    @ProtocolDsl
    inline fun <reified T : PacketCreator> send(noinline init: suspend T.() -> Unit = {}): Deferred<Unit> {
        return networkScope.async(Dispatchers.IO) {
            val packetInstance = createPacketInstance<T>(protocolManager = viewmodel.protocolManager)
            init(packetInstance)
            val jsonPacket = packetInstance.build()
            transmitPacket(jsonPacket, packetClass = T::class)
        }
    }

    suspend fun transmitPacket(json: SendablePacket, packetClass: KClass<out PacketCreator>? = null, isRetry: Boolean = false) {
        withContext(Dispatchers.IO) {
            try {
                if (isSocketValid()) {
                    val finalOut = json + "\r\n"
                    loggy("Client>>> $finalOut")
                    writeActualString(finalOut)
                } else {
                    /** Queuing any pending outgoing messages */
                    if (packetClass != PacketCreator.Hello::class && packetClass != null) {
                        viewmodel.sessionManager.session.outboundQueue.add(json)
                    }
                    if (state == Constants.CONNECTIONSTATE.STATE_CONNECTED) {
                        onError()
                    }
                }
            } catch (e: Exception) {
                loggy(e.stackTraceToString())
                if (isRetry) {
                    onError()
                } else {
                    transmitPacket(json, packetClass, isRetry = true)
                }
            }
        }
    }

}