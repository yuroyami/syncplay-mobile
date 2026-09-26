package app.protocol.sync

import app.protocol.Session
import app.protocol.wire.IgnoringOnTheFlyData
import app.protocol.wire.PlaystateData
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The sync decision, as a pure function that tests can call.
 *
 * [decideSync] takes what the server said plus a snapshot of where we are, and answers with the
 * next snapshot and a list of things to do. It touches nothing: no player, no network, no
 * preferences, no clock.
 *
 * The message handler (`onState`) stays in charge of doing those things, in order. The
 * thresholds match the reference client's `constants.py`.
 */

/** The sync anchor: everything `onState` carries from one message to the next. */
data class SyncState(
    val serverIgnFly: Int = 0,
    val clientIgnFly: Int = 0,
    val globalPaused: Boolean = true,
    val globalPositionMs: Double = 0.0,
    val lastGlobalPositionSetAt: Instant? = null,
    val lastGlobalUpdate: Instant? = null,
    val behindFirstDetected: Instant? = null,
    val speedChanged: Boolean = false,
    /** The rate controller's level: 0 is normal speed, -1 a nudge slower, 1 a nudge faster. */
    val nudgeLevel: Int = 0,
    /** The rate controller's filtered drift, in seconds. Null until the next steady message. */
    val smoothedDiff: Double? = null,
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
    /** True for a follower in a controlled room, the only case where PC forces a fast-forward. */
    val followerInControlledRoom: Boolean,
    val prefs: SyncPrefs,
    /** Seconds the inbound position is already stale: this message's own delay, or the ping service's estimate. */
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
    /** True while the position cache still belongs to a seek that Main has not applied. */
    val seekPending: Boolean = false,
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

    /** The rate controller sets the speed to [rate]. Silent: nobody can hear or see it. */
    data class Nudge(val rate: Double) : SyncAction
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

// The rate controller under the ladder. It has no counterpart in the reference client.
/** How far a nudge moves the speed from normal: half a percent, which nobody hears or sees. */
const val NUDGE_RATE = 0.005
/** The filtered drift, in seconds, at which a nudge starts. */
const val NUDGE_START = 0.15
/** The filtered drift, in seconds, under which a nudge stops. */
const val NUDGE_STOP = 0.03
/** How much of each new drift reading the filter takes in. */
const val NUDGE_SMOOTHING = 0.3

/**
 * The inbound length limit on a name, shared with the message handler. Cutting at the
 * protocol's 16 would break the server's own duplicate-name convention, where "alice" is
 * followed by "alice_".
 */
private const val MAX_USERNAME_CHARS = Session.MAX_USERNAME_CHARS

/**
 * Applies a server `ignoringOnTheFly` block. A server counter is adopted and clears ours; a
 * client counter that matches ours clears ours. Separate from [decideSync] because it runs even
 * for a State that carries no playstate.
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
     * diff looks like multi-second lag, which would fire a phantom catch-up notice. A
     * backgrounded client is paused on purpose and catches up when it returns. */
    if (ctx.hasMedia && !ctx.isInBackground && !ctx.seekPending) {
        if (diff > ctx.rewindThreshold && doSeek != true && ctx.prefs.rewind) {
            if (next.speedChanged) { actions += SyncAction.RestoreSpeed; next = next.copy(speedChanged = false) }
            actions += SyncAction.SomeoneBehind(setBy ?: "", agedPosition)
        }

        /* PC forces the catch-up only on a client that cannot control the room: only a follower
         * in a controlled room is forced to fast-forward. In a normal room everyone can control,
         * so the room follows its slowest member instead. dontSlowWithMe opts in regardless. */
        val canFastForward = ctx.prefs.fastForward && (ctx.followerInControlledRoom || ctx.prefs.dontSlowWithMe)
        /* The "behind" clock starts well before the trigger, so a user who moves the trigger
         * keeps the same lead time that the reference client gives. */
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

    // The rate controller works only on a message that nothing else acted on.
    val steady = actions.isEmpty() && ctx.hasMedia && !ctx.isInBackground && !ctx.seekPending && !paused &&
        doSeek != true && ctx.prefs.slowdown && ctx.supportsSpeedAdjustment && !next.speedChanged
    val canCatchUp = ctx.prefs.fastForward && (ctx.followerInControlledRoom || ctx.prefs.dontSlowWithMe)
    next = nudgeRate(next, actions, diff, steady, canCatchUp)

    return SyncOutcome(next, actions)
}

/**
 * The rate controller under the ladder. It filters the drift, so network jitter alone never moves
 * the rate, and it works inside the band that the ladder leaves alone. It sets one level of speed,
 * [NUDGE_RATE] from normal, and it adds an action only when the level changes, so an engine sees
 * few speed changes. It speeds up only a client that may catch up on its own ([canCatchUp]): in
 * any other room, the room waits for its slowest member.
 *
 * On a message that is not [steady], it starts over at normal speed. A ladder speed change sets
 * the speed itself; anything else (a seek, a pause, the first sync) leaves the nudge to end here.
 */
private fun nudgeRate(
    state: SyncState,
    actions: MutableList<SyncAction>,
    diff: Double,
    steady: Boolean,
    canCatchUp: Boolean,
): SyncState {
    if (!steady) {
        val ladderSetsSpeed = actions.any { it is SyncAction.SlowDown || it == SyncAction.RestoreSpeed }
        if (state.nudgeLevel != 0 && !ladderSetsSpeed) actions += SyncAction.Nudge(1.0)
        return state.copy(nudgeLevel = 0, smoothedDiff = null)
    }
    val smoothed = state.smoothedDiff?.let { it + NUDGE_SMOOTHING * (diff - it) } ?: diff
    val level = when (state.nudgeLevel) {
        0 -> when {
            smoothed > NUDGE_START -> -1
            smoothed < -NUDGE_START && canCatchUp -> 1
            else -> 0
        }
        -1 -> if (smoothed < NUDGE_STOP) 0 else -1
        else -> if (smoothed > -NUDGE_STOP || !canCatchUp) 0 else 1
    }
    if (level != state.nudgeLevel) actions += SyncAction.Nudge(1.0 + level * NUDGE_RATE)
    return state.copy(nudgeLevel = level, smoothedDiff = smoothed)
}
