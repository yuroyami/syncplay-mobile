package app.server.protocol.incoming

import app.protocol.server.State
import app.server.ClientConnection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Client-originated `State` (periodic sync packet). Reuses [State.StateData] — symmetric shape.
 *
 * Updates the watcher's pause/position/seek state and adjusts the client's "ignoringOnTheFly"
 * counters.
 */
@Serializable
data class IncomingState(
    @SerialName("State") val state: State.StateData
) : IncomingMessage {

    override suspend fun handle(connection: ClientConnection) {
        connection.handleIncomingState(state)
    }
}
