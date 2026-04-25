package app.protocol.wire

import kotlinx.serialization.Serializable

/**
 * Inner payload of a `State` message — symmetric across both directions.
 *
 * Server broadcasts authoritative state; client reports its own state back. Both sides
 * populate [playstate] and [ping]; [ignoringOnTheFly] is sent when feedback suppression
 * counters are active.
 */
@Serializable
data class StateData(
    val playstate: PlaystateData? = null,
    val ping: PingData? = null,
    val ignoringOnTheFly: IgnoringOnTheFlyData? = null
)
