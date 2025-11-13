package com.yuroyami.syncplay.managers.protocol

import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.managers.network.NetworkManager
import com.yuroyami.syncplay.managers.protocol.creator.PacketOut
import com.yuroyami.syncplay.managers.protocol.handler.PacketHandler
import com.yuroyami.syncplay.utils.ProtocolDsl
import com.yuroyami.syncplay.viewmodels.RoomViewmodel

/**
 * Manages the Syncplay protocol state and packet handling.
 *
 * The protocol manager acts as the central authority for what the "true" playback state is
 * according to the server, which local playback must sync with.
 *
 * @property viewmodel The parent RoomViewModel that owns this manager
 */
class ProtocolManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {
    /**
     * The authoritative paused state from the server.
     * Local player should match this state during synchronization.
     */
    var globalPaused: Boolean = true

    /**
     * The authoritative playback position from the server in milliseconds.
     * Local player seeks to this position during synchronization.
     */
    var globalPositionMs: Double = 0.0

    /**
     * Server's "ignore on the fly" counter.
     * Used to handle conflicting state updates during rapid changes.
     */
    var serverIgnFly: Int = 0

    /**
     * Client's "ignore on the fly" counter.
     * Used to prevent responding to our own state changes echoed by the server.
     * Until the server acknowledges our state changes.
     */
    var clientIgnFly: Int = 0

    /**
     * Time difference threshold in seconds to trigger a rewind/sync.
     * When position drift exceeds this threshold, a hard seek is performed.
     * Default: 12 seconds (as per official Syncplay specification, don't change)
     */
    val rewindThreshold = 12L

    /**
     * Service for managing protocol-level ping measurements.
     * Tracks round-trip time for synchronization calculations.
     */
    var pingService = PingService()

    /**
     * Handler for parsing and processing incoming protocol packets from the server.
     */
    @ProtocolDsl
    val packetHandler = PacketHandler(viewmodel, this)

    /**
     * Flag indicating the room is currently being changed.
     * Used to ignore stale packets from the old room during transitions, e.g when switching to a managed room.
     */
    var isRoomChanging = false

    /**
     * Resets all protocol state to initial values.
     * Called when disconnecting or leaving a room.
     */
    override fun invalidate() {
        globalPaused = true
        globalPositionMs = 0.0
        serverIgnFly = 0
        clientIgnFly = 0
        pingService = PingService()
    }

    companion object {
        /**
         * Factory method to create typed packet instances for sending.

         * @param T The packet type to create (e.g., PacketCreator.Hello)
         * @param protocolManager The protocol manager instance for context-dependent packets
         * @return A new instance of the requested packet type
         * @throws IllegalArgumentException if the packet type is unknown
         */
        @ProtocolDsl
        inline fun <reified T : PacketOut> NetworkManager.createPacketInstance(protocolManager: ProtocolManager): T {
            return when (T::class) {
                PacketOut.Hello::class -> PacketOut.Hello() as T
                PacketOut.Joined::class -> PacketOut.Joined() as T
                PacketOut.EmptyList::class -> PacketOut.EmptyList() as T
                PacketOut.Readiness::class -> PacketOut.Readiness() as T
                PacketOut.File::class -> PacketOut.File() as T
                PacketOut.Chat::class -> PacketOut.Chat() as T
                PacketOut.State::class -> PacketOut.State(protocolManager) as T
                PacketOut.PlaylistChange::class -> PacketOut.PlaylistChange() as T
                PacketOut.PlaylistIndex::class -> PacketOut.PlaylistIndex() as T
                PacketOut.ControllerAuth::class -> PacketOut.ControllerAuth() as T
                PacketOut.RoomChange::class -> PacketOut.RoomChange() as T
                PacketOut.TLS::class -> PacketOut.TLS() as T
                else -> throw IllegalArgumentException("Unknown packet type: ${T::class.simpleName}")
            }
        }
    }
}