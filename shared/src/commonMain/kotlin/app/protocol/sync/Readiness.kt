package app.protocol.sync

import app.protocol.models.User

/**
 * What the room's readiness actually says, and whether it is time to start playing.
 *
 * Mobile has only ever had the gate: a blocked unpause marks you ready and says nothing more.
 * The desktop client also tells you who everyone is waiting for and starts a countdown once
 * they are all there. Both of those are decisions, so they are functions.
 */

/** The room's readiness in one value, ready to be turned into a sentence. */
data class ReadinessSummary(
    /** Everyone but you who has a file open. */
    val peersWithFile: List<String>,
    /** The ones among them who are not ready yet, in roster order. */
    val notReady: List<String>,
) {
    /** Nobody else has a file open, so there is nobody to wait for. */
    val alone: Boolean get() = peersWithFile.isEmpty()

    /** Everyone with a file is ready. Vacuously true when you are alone. */
    val everyoneReady: Boolean get() = notReady.isEmpty()

    /** How many people the room is actually waiting on, you included. */
    val participantCount: Int get() = peersWithFile.size + 1
}

/**
 * Reads the roster. A user with no file is ignored throughout, matching the reference client:
 * someone who has not opened anything cannot be ready or unready in any meaningful sense.
 */
fun summariseReadiness(users: List<User>, selfName: String): ReadinessSummary {
    val peers = users.filter { it.name != selfName && it.file != null }
    return ReadinessSummary(
        peersWithFile = peers.map { it.name },
        notReady = peers.filterNot { it.readiness }.map { it.name },
    )
}

/** Why the countdown is or is not running, so the room can say something useful. */
sealed interface AutoplayState {
    /** Autoplay is off, or the room is in no position to start. */
    data object Idle : AutoplayState

    /** Waiting on these people. */
    data class Waiting(val notReady: List<String>) : AutoplayState

    /** Everyone is ready and the room starts in [secondsLeft]. */
    data class CountingDown(val secondsLeft: Int) : AutoplayState
}

/** How long the reference client waits before starting on its own. */
const val AUTOPLAY_COUNTDOWN_SECONDS = 3

/**
 * Whether a countdown should be running right now.
 *
 * Every condition is one the reference client also applies: autoplay has to be on, the room has
 * to be paused, you have to be able to control it, there has to be more than one person, and
 * everyone has to be ready. A room of one never counts down, because there is nothing to
 * synchronise with.
 */
fun shouldCountDown(
    autoplayEnabled: Boolean,
    roomPaused: Boolean,
    canControlRoom: Boolean,
    selfReady: Boolean,
    summary: ReadinessSummary,
): Boolean =
    autoplayEnabled &&
        roomPaused &&
        canControlRoom &&
        selfReady &&
        !summary.alone &&
        summary.everyoneReady
