package app.protocol.wire

import kotlinx.serialization.Serializable

/**
 * Controller-auth payload. The same model serves the request (client to server: [password]
 * and [room]) and the response (server to client: [user], [room] and [success]).
 *
 * [password] is nullable so the server's response leaves it off the wire (with
 * `explicitNulls = false`), matching the Python reference protocol.
 */
@Serializable
data class ControllerAuthData(
    val user: String? = null,
    val room: String? = null,
    val password: String? = null,
    val success: Boolean = false
)

/** Server-only: announces a newly created controlled room with its hashed name and raw password. */
@Serializable
data class NewControlledRoom(
    val password: String,
    val roomName: String
)
