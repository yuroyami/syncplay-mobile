package app.protocol.sync

import kotlin.math.abs
import kotlin.time.Instant

/**
 * What position to put on the wire, as a function.
 *
 * This is the other half of [decideSync]: the outbound side. Both were unreachable from a test
 * because they lived on managers that need the whole room, and both are places where a wrong
 * answer poisons everyone else's playback rather than just our own.
 *
 * The masking rule exists because a freshly loaded file reads near zero for a second or two. The
 * official server adopts its slowest watcher, so advertising that zero drags the whole room back
 * to the start of the file. While a load is settling we advertise the room's own position
 * instead, and stop the moment the engine catches up, proves it never can, or the deadline
 * passes, so a genuine standing desync stays visible.
 */

/** Everything the outbound position depends on. */
data class PositionInputs(
    val now: Instant,
    /** The last room position we were told, in milliseconds. */
    val globalPositionMs: Double,
    /** When we were told it. Null means never, so there is nothing to extrapolate from. */
    val globalPositionSetAt: Instant?,
    val globalPaused: Boolean,
    val hasMedia: Boolean,
    val isInBackground: Boolean,
    /** The engine's estimated position, in milliseconds. */
    val localPositionMs: Double,
    /** The file's duration in milliseconds, or 0 when unknown. */
    val durationMs: Double,
    /** Non-null while a fresh load is still settling; the instant masking gives up. */
    val awaitingRoomResyncDeadline: Instant?,
    /**
     * How far our copy runs ahead of the room's, in seconds. Subtracted from what we advertise,
     * so a room full of people with different rips still agrees on one position.
     */
    val userOffsetSeconds: Double = 0.0,
)

/** The position to send, and whether masking should stay armed. */
data class PositionReport(val positionSeconds: Double, val keepMasking: Boolean)

/** How close the engine must get to the room before we stop masking, in seconds. */
const val SEEK_THRESHOLD_SECONDS = 1.0

/** The room's position now, carried forward by wall clock while it plays. */
fun extrapolatedGlobalPositionMs(inputs: PositionInputs): Double {
    val anchor = inputs.globalPositionSetAt ?: return inputs.globalPositionMs
    if (inputs.globalPaused) return inputs.globalPositionMs
    return inputs.globalPositionMs + (inputs.now - anchor).inWholeMilliseconds.toDouble()
}

/** Decides what to advertise. */
fun reportablePosition(inputs: PositionInputs): PositionReport {
    val globalMs = extrapolatedGlobalPositionMs(inputs)

    // No file: nothing local to report. The server pushes file-less watchers last anyway, but
    // this keeps us from ever announcing a bare zero.
    if (!inputs.hasMedia) return PositionReport(globalMs / 1000.0, keepMasking = true)

    // Paused in the background: the room must not adopt a frozen watcher as its slowest.
    if (inputs.isInBackground) return PositionReport(globalMs / 1000.0, keepMasking = true)

    // What the room should hear: our position, less the offset that is ours alone.
    val localAsRoomSeesIt = inputs.localPositionMs / 1000.0 - inputs.userOffsetSeconds

    val deadline = inputs.awaitingRoomResyncDeadline
        ?: return PositionReport(localAsRoomSeesIt, keepMasking = false)

    val thresholdMs = SEEK_THRESHOLD_SECONDS * 1000.0
    val converged = abs(localAsRoomSeesIt * 1000.0 - globalMs) <= thresholdMs
    // A file shorter than the room position can never catch up; a mismatched file does this.
    val cannotCatchUp = inputs.durationMs > 0.0 && globalMs >= inputs.durationMs - thresholdMs
    val timedOut = inputs.now >= deadline

    return if (converged || cannotCatchUp || timedOut) {
        PositionReport(localAsRoomSeesIt, keepMasking = false)
    } else {
        PositionReport(globalMs / 1000.0, keepMasking = true)
    }
}
