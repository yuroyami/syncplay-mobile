package app.protocol.server

import kotlinx.serialization.Serializable

/**
 * Base sealed interface for all incoming Syncplay protocol messages.
 *
 * Each implementation provides a [handle] method executed within a [PacketHandler] context,
 * giving access to the viewmodel, sender, protocol state, and UI callbacks.
 */
@Serializable
sealed interface ServerMessage {

    context(packetHandler: PacketHandler)
    suspend fun handle()

}