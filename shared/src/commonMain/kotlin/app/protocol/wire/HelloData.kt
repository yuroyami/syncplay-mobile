package app.protocol.wire

import app.protocol.models.RoomFeatures
import kotlinx.serialization.Serializable

/**
 * Inner payload of a `Hello` message — handshake exchanged in both directions.
 *
 * Direction-specific fields:
 * - [password] — pre-hashed (MD5) server password. Set by client only.
 * - [motd] — server's message of the day. Set by server only.
 *
 * All other fields are common: [username], [room], [version]/[realversion], [features].
 */
@Serializable
data class HelloData(
    val username: String? = null,
    val password: String? = null,
    val room: Room? = null,
    val version: String? = null,
    val realversion: String? = null,
    val features: RoomFeatures = RoomFeatures(),
    val motd: String? = null
)
