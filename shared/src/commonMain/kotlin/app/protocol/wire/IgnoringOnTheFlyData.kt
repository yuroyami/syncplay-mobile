package app.protocol.wire

import kotlinx.serialization.Serializable

/** Counters carried in `State` to suppress feedback loops during rapid state changes. */
@Serializable
data class IgnoringOnTheFlyData(
    val server: Int? = null,
    val client: Int? = null
)
