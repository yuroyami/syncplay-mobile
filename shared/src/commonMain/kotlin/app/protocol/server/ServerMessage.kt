package app.protocol.server

import app.protocol.ProtocolManager
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import app.room.event.RoomEventHandler
import kotlinx.serialization.Serializable

/**
 * Base sealed interface for all incoming Syncplay protocol messages.
 */
@Serializable
sealed interface ServerMessage {

    suspend fun handle(
        protocol: ProtocolManager,
        viewmodel: RoomViewmodel,
        dispatcher: NetworkManager,
        callback: RoomEventHandler
    )

}