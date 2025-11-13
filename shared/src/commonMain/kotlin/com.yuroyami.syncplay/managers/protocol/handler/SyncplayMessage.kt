package com.yuroyami.syncplay.managers.protocol.handler

import kotlinx.serialization.Serializable
/**
 * Base sealed interface for all incoming Syncplay protocol messages.
 *
 * This interface defines the contract for message handling in the Syncplay protocol implementation.
 * Each message type that implements this interface provides its own [handle] method that
 * processes the message according to the Syncplay protocol specification.
 *
 * The [handle] method is executed with a [PacketHandler] receiver context, providing access to the viewmodel.
 *
 * Messages are serialized using Kotlinx Serialization for JSON encoding/decoding in network communication.

 */
@Serializable
sealed interface SyncplayMessage {
    /**
     * Processes and handles the incoming Syncplay message.
     *
     * This suspend function is called when a message is received from the Syncplay server.
     * Implementations should:
     * - Update local state based on the message content
     * - Apply synchronization changes to the media player
     * - Trigger appropriate UI updates via callbacks
     * - Send any necessary response messages back to the server
     *
     * The function executes within the [PacketHandler] context, providing access to:
     * - [packetHandler.viewmodel] for UI state management
     * - [packetHandler.sender] for sending response packets
     * - [packetHandler.protocol] for protocol-level state
     * - [packetHandler.callback] for UI event notifications
     *
     * @receiver [PacketHandler] The protocol handler context providing access to synchronization
     *         state, player control, and network communication facilities.
     */
    context(packetHandler: PacketHandler)
    suspend fun handle()
}