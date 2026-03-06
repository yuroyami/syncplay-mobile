package app.protocol.server

import app.protocol.ProtocolManager
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import app.protocol.event.RoomEventHandler
import app.utils.ProtocolApi
import kotlinx.serialization.Serializable

/**
 * Base sealed interface for all incoming Syncplay protocol messages.
 */
@Serializable
sealed interface ServerMessage {

    @ProtocolApi
    suspend fun handle(
        protocol: ProtocolManager,
        viewmodel: RoomViewmodel,
        dispatcher: NetworkManager,
        callback: RoomEventHandler
    )

}