package app.protocol.wire

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistChangeData(
    val user: String? = null,
    val files: List<String>? = null
)

@Serializable
data class PlaylistIndexData(
    val user: String? = null,
    val index: Int? = null
)
