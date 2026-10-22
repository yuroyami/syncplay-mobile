package app.protocol.wire

import kotlinx.serialization.Serializable

/**
 * Inner payload of a `State` message, with the same shape in both directions.
 *
 * The server broadcasts the room's state; the client reports its own state back. Both sides
 * send [ping]. The client leaves out [playstate] when it has no position to report, or while
 * it ignores the server on the fly. [ignoringOnTheFly] is sent while either feedback
 * suppression counter is non-zero.
 */
@Serializable
data class StateData(
    val playstate: PlaystateData? = null,
    val ping: PingData? = null,
    val ignoringOnTheFly: IgnoringOnTheFlyData? = null
)
