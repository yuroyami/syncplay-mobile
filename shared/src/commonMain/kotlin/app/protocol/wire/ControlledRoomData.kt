package app.protocol.wire

import kotlinx.serialization.Serializable

/**
 * Controller-auth payload — same model for the request (client→server: [password] + [room])
 * and the response (server→client: [user] + [room] + [success]).
 *
 * [password] is nullable so the server's response omits it on the wire (with
 * `explicitNulls = false`), matching the python reference protocol.
 */
@Serializable
data class ControllerAuthData(
    val user: String? = null,
    val room: String? = null,
    val password: String? = null,
    val success: Boolean = false
)

/** Server-only: announces a newly minted controlled room with its hashed name + raw password. */
@Serializable
data class NewControlledRoom(
    val password: String,
    val roomName: String
)
