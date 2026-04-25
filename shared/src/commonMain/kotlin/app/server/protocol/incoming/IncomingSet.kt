package app.server.protocol.incoming

import app.protocol.server.Set
import app.server.ClientConnection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Client-originated `Set` (multi-purpose command). Reuses [Set.SetData] — same wire shape,
 * with the client populating room/file/ready/playlistChange/playlistIndex/controllerAuth/features.
 */
@Serializable
data class IncomingSet(
    @SerialName("Set") val set: Set.SetData
) : IncomingMessage {

    override suspend fun handle(connection: ClientConnection) {
        set.room?.let { connection.handleSetRoom(it) }
        set.file?.let { connection.handleSetFile(it) }
        set.ready?.let { connection.handleSetReady(it) }
        set.controllerAuth?.let { connection.handleSetControllerAuth(it) }
        set.playlistChange?.let { connection.handleSetPlaylistChange(it) }
        set.playlistIndex?.let { connection.handleSetPlaylistIndex(it) }
        set.features?.let { connection.handleSetFeatures(it) }
    }
}
