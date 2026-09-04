package app.protocol.sync

import app.protocol.wire.IgnoringOnTheFlyData
import app.protocol.wire.PlaystateData
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The sync decision, as a function.
 *
 * `onState` used to read a dozen fields, mutate eight of them and fire six kinds of side effect
 * in one 150-line method, which is why the sync algorithm had no tests: there was nothing to
 * call. [decideSync] takes what the server said plus a snapshot of where we are, and answers
 * with the next snapshot and a list of things to do. It touches nothing: no player, no network,
 * no preferences, no clock.
 *
 * The handler stays in charge of doing them, in order. Behaviour is meant to be identical to
 * what the method did, line for line; the thresholds and their reasons are unchanged and still
 * match the reference client's `constants.py`.
 */

/** The sync anchor: everything `onState` carried between messages. */
data class SyncState(
    val serverIgnFly: Int = 0,
    val clientIgnFly: Int = 0,
    val globalPaused: Boolean = true,
    val globalPositionMs: Double = 0.0,
    val lastGlobalPositionSetAt: Instant? = null,
    val lastGlobalUpdate: Instant? = null,
    val behindFirstDetected: Instant? = null,
    val speedChanged: Boolean = false,
)

/** The four sync preferences, read once so the decision does not touch storage. */
data class SyncPrefs(
    val rewind: Boolean,
    val fastForward: Boolean,
    val slowdown: Boolean,
    val dontSlowWithMe: Boolean,
)

/** Everything true at this instant that the decision needs and does not own. */
data class SyncContext(
    val now: Instant,
    /** The engine's estimated position, in milliseconds. Never a live probe. */
    val playerPositionMs: Double,
    val hasMedia: Boolean,
    val isInBackground: Boolean,
    val supportsSpeedAdjustment: Boolean,
    val selfName: String,
    /** True for a follower in a controlled room, which is the only case PC force-fastforwards. */
    val followerInControlledRoom: Boolean,
    val prefs: SyncPrefs,
    /** Seconds the inbound position is already stale, from the ping service. */
    val messageAge: Double,
    /**
     * How far our copy of the file runs ahead of the room's, in seconds.
     *
     * Two rips of the same film can differ by an intro, a logo card or a few frames of black.
     * A positive offset means our copy needs to sit that much further in to show the same
     * picture. It shifts only what we do locally; the position we advertise is converted back
     * so the rest of the room is unaffected.
     */
    val userOffsetSeconds: Double = 0.0,
    /** The three drift thresholds, in seconds. Defaults match the reference client. */
    val rewindThreshold: Double = REWIND_THRESHOLD,
    val slowdownThreshold: Double = SLOWDOWN_THRESHOLD,
    val fastForwardThreshold: Double = FASTFORWARD_THRESHOLD,
)

/** What the handler should do, in the order given. */
sealed interface SyncAction {
    /** No anchor yet and media is loaded: hard-seek and apply the room's pause state. */
    data class FirstSync(val seekToMs: Long, val paused: Boolean) : SyncAction
    data class SomeoneSeeked(val by: String, val toSeconds: Double) : SyncAction
    data class SomeoneBehind(val by: String, val toSeconds: Double) : SyncAction
    data class SomeoneFastForwarded(val by: String, val toSeconds: Double) : SyncAction
    data class SlowDown(val by: String) : SyncAction
    data object RestoreSpeed : SyncAction
    data class SomeonePlayed(val by: String) : SyncAction
    data class SomeonePaused(val by: String) : SyncAction
}

/** The next anchor plus the work to do. */
data class SyncOutcome(val state: SyncState, val actions: List<SyncAction>)

// Thresholds, matching the reference client's constants.py.
const val REWIND_THRESHOLD = 4.0
const val FASTFORWARD_BEHIND_THRESHOLD = 1.75
const val FASTFORWARD_THRESHOLD = 5.0
const val FASTFORWARD_EXTRA_TIME = 0.25
const val FASTFORWARD_RESET_THRESHOLD = 3.0
const val SLOWDOWN_RATE = 0.95
const val SLOWDOWN_THRESHOLD = 1.5
const val SLOWDOWN_RESET_THRESHOLD = 0.1

/** Longest username the protocol allows; names arriving longer than this are cut. */
private const val MAX_USERNAME_CHARS = 16

/**
 * Applies a server `ignoringOnTheFly` block. A server counter adopts it and clears ours; a
 * client counter matching ours clears ours. Separate from [decideSync] because it runs even for
 * a State that carries no playstate.
 */
fun SyncState.withIgnoringOnTheFly(ignoring: IgnoringOnTheFlyData?): SyncState {
    if (ignoring == null) return this
    return when {
        ignoring.server != null -> copy(serverIgnFly = ignoring.server, clientIgnFly = 0)
        ignoring.client != null && clientIgnFly == ignoring.client -> copy(clientIgnFly = 0)
        else -> this
    }
}

/**
 * Decides what a `State` means. Returns the unchanged state and no actions when the message
 * carries no usable playstate, or while we are ignoring the server on the fly.
 */
fun decideSync(playstate: PlaystateData?, state: SyncState, ctx: SyncContext): SyncOutcome {
    val position = playstate?.position ?: 0.0
    val paused = playstate?.paused
    val doSeek = playstate?.doSeek
    val setBy = playstate?.setBy

    if (playstate == null || paused == null || state.clientIgnFly != 0) {
        return SyncOutcome(state, emptyList())
    }

    // When the room is playing, the position the server sent is already messageAge seconds
    // stale, so the position to compare against is position + messageAge. The user's own offset
    // shifts that to where *our* copy should be.
    val roomPosition = if (paused) position else position + ctx.messageAge
    val agedPosition = roomPosition + ctx.userOffsetSeconds

    /* ONLY a genuine room-state transition: the last room pause-state we recorded differs from
     * what the server just sent. Deliberately not compared against a live isPlaying(): that
     * fires on local player drift rather than a transition, and VLCKit's stale async value
     * would re-announce on every 1 Hz State. */
    val pausedChanged = state.globalPaused != paused
    val diff = (ctx.playerPositionMs / 1000.0) - agedPosition

    val actions = mutableListOf<SyncAction>()
    var next = state.copy(
        globalPaused = paused,
        // The anchor stays in the room's own frame; the offset is ours alone.
        globalPositionMs = roomPosition * 1000.0,
        lastGlobalPositionSetAt = ctx.now,
    )

    if (state.lastGlobalUpdate == null && ctx.hasMedia) {
        actions += SyncAction.FirstSync((agedPosition * 1000.0).toLong(), paused)
    }
    next = next.copy(lastGlobalUpdate = ctx.now)

    if (doSeek == true && setBy != null) {
        if (next.speedChanged) { actions += SyncAction.RestoreSpeed; next = next.copy(speedChanged = false) }
        actions += SyncAction.SomeoneSeeked(setBy.take(MAX_USERNAME_CHARS), agedPosition)
    }

    /* Desync correction only makes sense with media loaded. With none, the engine reads 0 and
     * diff looks like multi-second lag, which used to fire a phantom catch-up notice. A
     * backgrounded client is paused on purpose and catches up when it returns. */
    if (ctx.hasMedia && !ctx.isInBackground) {
        if (diff > ctx.rewindThreshold && doSeek != true && ctx.prefs.rewind) {
            if (next.speedChanged) { actions += SyncAction.RestoreSpeed; next = next.copy(speedChanged = false) }
            actions += SyncAction.SomeoneBehind(setBy ?: "", agedPosition)
        }

        /* PC gates the forced catch-up on not being able to control the room: only a follower
         * in a controlled room force-fastforwards. In a normal room everyone can control, so
         * the room follows its slowest member instead. dontSlowWithMe opts in regardless. */
        val canFastForward = ctx.prefs.fastForward && (ctx.followerInControlledRoom || ctx.prefs.dontSlowWithMe)
        /* The "behind" clock starts well before the trigger, so a user who moves the trigger
         * keeps the same head start the reference client gives. */
        val behindThreshold = ctx.fastForwardThreshold - (FASTFORWARD_THRESHOLD - FASTFORWARD_BEHIND_THRESHOLD)
        if (diff < -behindThreshold && doSeek != true && canFastForward) {
            val since = next.behindFirstDetected
            if (since == null) {
                next = next.copy(behindFirstDetected = ctx.now)
            } else {
                val secondsBehind = (ctx.now - since).inWholeMilliseconds / 1000.0
                if (secondsBehind > (ctx.fastForwardThreshold - behindThreshold) &&
                    diff < -ctx.fastForwardThreshold
                ) {
                    actions += SyncAction.SomeoneFastForwarded(setBy ?: "", agedPosition + FASTFORWARD_EXTRA_TIME)
                    next = next.copy(behindFirstDetected = ctx.now + FASTFORWARD_RESET_THRESHOLD.seconds)
                }
            }
        } else {
            next = next.copy(behindFirstDetected = null)
        }

        if (doSeek != true && !paused) {
            if (ctx.prefs.slowdown && ctx.supportsSpeedAdjustment) {
                if (diff > ctx.slowdownThreshold && !next.speedChanged) {
                    if (setBy != null && setBy != ctx.selfName) {
                        actions += SyncAction.SlowDown(setBy.take(MAX_USERNAME_CHARS))
                        next = next.copy(speedChanged = true)
                    }
                } else if (next.speedChanged && diff < SLOWDOWN_RESET_THRESHOLD) {
                    actions += SyncAction.RestoreSpeed
                    next = next.copy(speedChanged = false)
                }
            } else if (next.speedChanged) {
                actions += SyncAction.RestoreSpeed
                next = next.copy(speedChanged = false)
            }
        }
    }

    if (pausedChanged) {
        val who = (setBy ?: "").take(MAX_USERNAME_CHARS)
        if (paused) {
            if (next.speedChanged) { actions += SyncAction.RestoreSpeed; next = next.copy(speedChanged = false) }
            actions += SyncAction.SomeonePaused(who)
        } else {
            actions += SyncAction.SomeonePlayed(who)
        }
    }

    return SyncOutcome(next, actions)
}
