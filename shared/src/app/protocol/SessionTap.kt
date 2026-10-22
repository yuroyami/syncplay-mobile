package app.protocol

import app.protocol.sync.SyncContext
import app.protocol.sync.SyncOutcome
import app.protocol.sync.SyncState
import app.protocol.wire.PlaystateData

/**
 * Sees every protocol line and every sync decision of one room, in the order they happen. Only the
 * recorded-session tests set one: they record a real session and replay it with no network.
 */
interface SessionTap {
    /** A line as it arrives from the server ([inbound]), or as it leaves for the server. */
    fun line(inbound: Boolean, line: String)

    /** One sync decision: what the server said, the state and context of the decision, and its result. */
    fun decision(playstate: PlaystateData?, before: SyncState, ctx: SyncContext, outcome: SyncOutcome)
}
