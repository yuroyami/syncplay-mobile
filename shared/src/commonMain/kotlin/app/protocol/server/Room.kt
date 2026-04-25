package app.protocol.server

import kotlinx.serialization.Serializable

/** Room reference used in protocol messages: `{"name": "..."}`. */
@Serializable
data class Room(val name: String)
