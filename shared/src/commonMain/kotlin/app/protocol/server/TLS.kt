package app.protocol.server

import app.protocol.ProtocolManager
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import app.protocol.event.RoomCallback
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TLS negotiation message. When received, triggers [RoomCallback]
 * to upgrade the connection from plain TCP to TLS.
 */
@Serializable
data class TLS(
    @SerialName("TLS")
    val tls: TLSData
) : ServerMessage {

    override suspend fun handle(
        protocol: ProtocolManager,
        viewmodel: RoomViewmodel,
        dispatcher: NetworkManager,
        callback: RoomCallback
    ) {
        tls.startTLS?.let { startTLS ->
            callback.onReceivedTLS(startTLS)
        }
    }

    /** @property startTLS True if the server requests a TLS upgrade. */
    @Serializable
    data class TLSData(
        val startTLS: Boolean? = null
    )
}