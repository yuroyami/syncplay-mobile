package com.yuroyami.syncplay.viewmodel.managers

import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.models.Session
import com.yuroyami.syncplay.protocol.parsing.JsonHandler
import com.yuroyami.syncplay.protocol.sending.Packet
import com.yuroyami.syncplay.protocol.sending.Packet.Companion.createPacketInstance
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.settings.valueSuspendingly
import com.yuroyami.syncplay.utils.PLATFORM
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.platform
import com.yuroyami.syncplay.viewmodel.AbstractManager
import com.yuroyami.syncplay.viewmodel.SyncplayViewmodel
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

    val networkJob = SupervisorJob()
    val networkScope = CoroutineScope(Dispatchers.IO + networkJob)

    /** Late-initialized socket channel which will host all incoming and outcoming data **/
    var state: Constants.CONNECTIONSTATE = Constants.CONNECTIONSTATE.STATE_DISCONNECTED
    var tls: Constants.TLS = Constants.TLS.TLS_NO

    open val engine: NetworkEngine = NetworkEngine.SWIFTNIO

    enum class NetworkEngine {
        KTOR,
        NETTY,
        SWIFTNIO
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
        viewmodel.onConnectionAttempt()
        state = Constants.CONNECTIONSTATE.STATE_CONNECTING

        /** Bootstrapping our Ktor client  */
        try {
            connectSocket()

            /** if the TLS mode is [Constants.TLS.TLS_ASK], then the the first packet to send
             * concerns an opportunistic TLS check with the server, otherwise, a Hello would be first */
            if (tls == Constants.TLS.TLS_ASK) {
                send<Packet.TLS>().await()
            } else {
                send<Packet.Hello> {
                    username = session.currentUsername
                    roomname = session.currentRoom
                    serverPassword = session.currentPassword
                }.await()
            }
        } catch (e: Exception) {
            loggy(e.stackTraceToString())
            syncplayCallback.onConnectionFailed()
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
            JsonHandler.parse(this@SyncplayProtocol, data)
        }
    }

    private fun onError() {
        syncplayCallback.onDisconnected()
    }

    fun terminateScope() {
        runCatching {
            networkScope.cancel()
        }
    }

    /** WRITING: This small method basically checks if the channel is active and writes to it, otherwise
     *  it queues the json to send in a special queue until the connection recovers. */
    inline fun <reified T : Packet> send(noinline init: T.() -> Unit = {}): Deferred<Unit> = protoScope.async(Dispatchers.IO) {
        val packetInstance = createPacketInstance<T>().apply(init)
        val jsonPacket = packetInstance.build()
        transmitPacket(jsonPacket, packetClass = T::class)
    }

    suspend fun transmitPacket(json: Packet.Companion.SendablePacket, packetClass: KClass<out Packet>? = null, isRetry: Boolean = false) {
        withContext(Dispatchers.IO) {
            try {
                if (isSocketValid()) {
                    val finalOut = json + "\r\n"
                    loggy("Client>>> $finalOut")
                    writeActualString(finalOut)
                } else {
                    /** Queuing any pending outgoing messages */
                    if (packetClass != Packet.Hello::class && packetClass != null) {
                        session.outboundQueue.add(json)
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