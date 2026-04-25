package app.protocol.server

import kotlinx.serialization.Serializable

/**
 * Counters carried in [State] to suppress feedback loops during rapid state changes.
 * Same shape both directions.
 */
@Serializable
data class IgnoringOnTheFlyData(
    val server: Int? = null,
    val client: Int? = null
)
