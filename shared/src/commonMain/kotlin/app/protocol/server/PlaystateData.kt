package app.protocol.server

import kotlinx.serialization.Serializable

/**
 * Playback state inside a [State] message. Symmetric across both directions:
 * server broadcasts authoritative state, client reports its own state back.
 *
 * @property setBy Username of the user who initiated this state change. Server-set only.
 */
@Serializable
data class PlaystateData(
    val doSeek: Boolean? = null,
    val position: Double? = null,
    val setBy: String? = null,
    val paused: Boolean? = null
)
