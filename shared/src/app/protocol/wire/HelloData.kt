package app.protocol.wire

import app.protocol.models.LenientRoomFeaturesSerializer
import app.protocol.models.RoomFeatures
import kotlinx.serialization.Serializable

/**
 * Inner payload of a `Hello` message: the handshake, exchanged in both directions.
 *
 * Direction-specific fields:
 * - [password]: the server password, pre-hashed with MD5. Set by the client only.
 * - [motd]: the server's message of the day. Set by the server only.
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
    @Serializable(with = LenientRoomFeaturesSerializer::class)
    val features: RoomFeatures = RoomFeatures(),
    val motd: String? = null
)
