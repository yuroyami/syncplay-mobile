package com.yuroyami.syncplay.managers.network

import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.managers.protocol.ProtocolManager.Companion.createPacketInstance
import com.yuroyami.syncplay.managers.protocol.creator.PacketOut
import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.utils.PLATFORM
import com.yuroyami.syncplay.utils.ProtocolDsl
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.platform
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

/**
 * Abstract base class for managing network connections to Syncplay servers.
 *
 * Provides a platform-independent interface for TCP/TLS socket communication with the
 * Syncplay server. Concrete implementations handle platform-specific networking (Ktor,
 * Netty, SwiftNIO).
 *
 * @property viewmodel The parent RoomViewModel that owns this manager
 */
abstract class NetworkManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    /**
     * The network engine implementation being used.
     */
    open val engine: NetworkEngine = NetworkEngine.SWIFTNIO

    /**
     * Current connection state with the Syncplay server.
     */
    var state: Constants.CONNECTIONSTATE = Constants.CONNECTIONSTATE.STATE_DISCONNECTED

    /**
     * TLS encryption mode for the connection.
     * Can be: TLS_NO (plain TCP), TLS_YES (encrypted), or TLS_ASK (check support with the server).
     */
    var tls: Constants.TLS = Constants.TLS.TLS_NO

    /**
     * Available network engine implementations.
     */
    enum class NetworkEngine {
        /** Ktor TCP client (cross-platform), doesn't support TLS (yet) */
        KTOR,
        /** Netty TCP client (Android), supports TLS */
        NETTY,
        /** SwiftNIO TCP client (iOS), supports TLS */
        SWIFTNIO
    }

    companion object {
        /**
         * Gets the user's preferred network engine from settings.
         *
         * Falls back to platform defaults: Netty on Android, SwiftNIO on iOS.
         *
         * @return The preferred NetworkEngine enum value
         */
        fun getPreferredEngine(): NetworkEngine {
            val defaultEngine = if (platform == PLATFORM.Android) NetworkEngine.NETTY else NetworkEngine.SWIFTNIO
            val engineName = pref(DataStoreKeys.PREF_NETWORK_ENGINE, defaultEngine.name.lowercase())
            return NetworkEngine.valueOf(engineName.uppercase())
        }
    }

    /**
     * Cleans up network resources and resets connection state.
     * Terminates any active connections and resets TLS mode.
     */
    override fun invalidate() {
        terminateExistingConnection()
        state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED
        tls = Constants.TLS.TLS_NO
    }

    /**
     * Initiates a connection to the Syncplay server.
     *
     * Terminates any existing connection, notifies callbacks, establishes a new socket
     * connection, and sends the initial handshake (TLS check or Hello packet).
     *
     * @throws Exception if connection fails (caught and triggers onConnectionFailed callback)
     */
    open suspend fun connect() {
        terminateExistingConnection()

        /** Informing UI controllers that we are starting a connection attempt */
        viewmodel.callbackManager.onConnectionAttempt()
        state = Constants.CONNECTIONSTATE.STATE_CONNECTING

        /** Bootstrapping our Ktor client  */
        try {
            connectSocket()

            /** if the TLS mode is [Constants.TLS.TLS_ASK], then the the first packet to send
             * concerns an opportunistic TLS check with the server, otherwise, a Hello would be first */
            if (tls == Constants.TLS.TLS_ASK) {
                send<PacketOut.TLS>()
            } else {
                send<PacketOut.Hello> {
                    username = viewmodel.sessionManager.session.currentUsername
                    roomname = viewmodel.sessionManager.session.currentRoom
                    serverPassword = viewmodel.sessionManager.session.currentPassword
                }
            }
        } catch (e: Exception) {
            loggy(e.stackTraceToString())
            viewmodel.callbackManager.onConnectionFailed()
        }
    }

    /**
     * Establishes the actual TCP socket connection to the server.
     *
     * Uses host and port from [com.yuroyami.syncplay.managers.SessionManager.Session].
     * Platform-specific implementation handles the actual socket creation.
     */
    abstract suspend fun connectSocket()

    /**
     * Checks whether the current network engine supports TLS encryption.
     *
     * @return true if TLS is supported (Netty, SwiftNIO), false otherwise (Ktor)
     */
    abstract fun supportsTLS(): Boolean

    /**
     * Terminates the current connection and cancels all read/write operations.
     *
     * Cleans up all network resources and disposes of any references to prevent leaks.
     */
    abstract fun terminateExistingConnection()

    /**
     * Writes a string directly to the socket.
     *
     * Low-level write operation - callers use [transmitPacket] instead as it encapsulates this.
     *
     * @param s The string to write to the socket
     */
    abstract suspend fun writeActualString(s: String)

    /**
     * Upgrades the current plain TCP connection to a TLS-encrypted connection.
     *
     * Called after the server confirms TLS support during the handshake.
     */
    abstract fun upgradeTls()

    /**
     * Schedules automatic reconnection after disconnection.
     *
     * Only schedules if currently disconnected. Uses configurable reconnection interval
     * from user preferences. Prevents multiple simultaneous reconnection attempts.
     */
    private var reconnectionJob: Job? = null
    fun reconnect() {
        if (state == Constants.CONNECTIONSTATE.STATE_DISCONNECTED) {
            if (reconnectionJob == null || reconnectionJob?.isCompleted == true) {
                reconnectionJob = viewmodel.viewModelScope.launch(Dispatchers.IO) {
                    state = Constants.CONNECTIONSTATE.STATE_SCHEDULING_RECONNECT
                    val reconnectionInterval = pref(DataStoreKeys.PREF_INROOM_RECONNECTION_INTERVAL, 2) * 1000L

                    delay(reconnectionInterval)

                    connect()
                }
            }
        }
    }

    /**
     * Processes an incoming packet from the server.
     *
     * Delegates parsing to the protocol manager on the Default dispatcher.
     *
     * @param data The raw packet data (JSON string)
     */
    fun handlePacket(data: String) {
        viewmodel.viewModelScope.launch(Dispatchers.Default) {
            viewmodel.protocolManager.packetHandler.parse(data)
        }
    }

    /**
     * Handles network errors by triggering the disconnection callback.
     *
     * Called when socket operations fail or timeout.
     */
    private fun onError() {
        loggy("ON ERRORRRRRRRRRRRRRRRR")
        viewmodel.callbackManager.onDisconnected()
    }

    /**
     * Type alias for JSON packet strings ready for transmission.
     */
    typealias SendablePacket = String

    /**
     * Asynchronously sends a protocol packet to the server.
     *
     * Creates and returns a Deferred that completes when the packet is sent.
     * Useful for fire-and-forget packet sending without blocking (can be used from non-suspend scopes)
     *
     * @param T The PacketCreator type to instantiate
     * @param init Lambda to configure the packet before sending
     * @return Deferred<Unit> that completes when the packet is sent
     */
    @ProtocolDsl
    inline fun <reified T : PacketOut> sendAsync(noinline init: suspend T.() -> Unit = {}): Deferred<Unit> {
        return viewmodel.viewModelScope.async(Dispatchers.IO) {
            send(init)
        }
    }

    /**
     * Sends a protocol packet to the server.
     *
     * Creates a packet instance, applies configuration, serializes to JSON,
     * and transmits to the server. Suspends until transmission completes.
     *
     * @param T The PacketCreator type to instantiate (e.g., PacketCreator.State)
     * @param init Lambda to configure the packet fields before sending
     */
    @ProtocolDsl
    suspend inline fun <reified T : PacketOut> send(noinline init: suspend T.() -> Unit = {}) {
        val packetInstance = createPacketInstance<T>(protocolManager = viewmodel.protocolManager)
        init(packetInstance)
        val jsonPacket = Json.encodeToString(packetInstance.build())
        transmitPacket(jsonPacket, packetClass = T::class)
    }

    /**
     * Transmits a JSON packet string to the server with retry logic.
     *
     * Appends CRLF terminator, logs the packet, and writes to the socket with a 10-second
     * timeout. Retries up to 3 times on failure before queuing and triggering error handling.
     *
     * @param json The JSON packet string to transmit
     * @param packetClass The packet type (for queueing on failure)
     * @param retryCounter Current retry attempt count (internal use)
     */
    suspend fun transmitPacket(json: SendablePacket, packetClass: KClass<out PacketOut>? = null, retryCounter: Int = 0) {
        withContext(Dispatchers.IO) {
            try {
                withTimeout(10.seconds) {
                    val finalOut = json + "\r\n"
                    loggy("Client>>> $finalOut")
                    writeActualString(finalOut)
                }
            } catch (e: Exception) {
                loggy(e.stackTraceToString())
                if (retryCounter >= 3) {
                    loggy("SOCKET INVALID")
                    /** Queuing any pending outgoing messages */
                    if (packetClass != PacketOut.Hello::class && packetClass != null) {
                        //viewmodel.sessionManager.session.outboundQueue.add(json)
                    }
                    onError()
                } else {
                    transmitPacket(json, packetClass, retryCounter = +retryCounter)
                }
            }
        }
    }

}