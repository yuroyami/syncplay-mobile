package app.protocol.sync

import app.protocol.wire.PlaystateData
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The rate controller under the correction ladder, in a closed loop: a simulated player and room,
 * one `State` a second, in synthetic time. The decision's speed changes and seeks go back into
 * the simulated player, as the message handler applies them.
 */
class RateControllerTest {

    private val t0 = Instant.fromEpochSeconds(1_000_000)

    /**
     * One simulated session. The local player runs at [drift] times its speed (1.002 is 0.2
     * percent fast), and the room runs at normal speed. [ageError] is how wrong the message age is
     * on each message, in seconds.
     */
    private inner class Session(
        private val drift: Double,
        private val ageError: (Int) -> Double = { 0.0 },
        private val follower: Boolean = false,
        start: Double = 0.0,
    ) {
        var rate = 1.0
        var local = start
        var room = 0.0
        var state = SyncState(globalPaused = false, lastGlobalUpdate = t0)
        val rates = mutableListOf<Double>()
        val errors = mutableListOf<Double>()
        val actions = mutableListOf<SyncAction>()

        fun run(seconds: Int, doSeekAt: Int? = null) = repeat(seconds) { i ->
            room += 1.0
            local += rate * drift
            val delay = 0.05
            val outcome = decideSync(
                playstate = PlaystateData(position = room - delay, paused = false, doSeek = if (i == doSeekAt) true else null, setBy = "peer"),
                state = state,
                ctx = SyncContext(
                    now = t0 + (i + 1).seconds,
                    playerPositionMs = local * 1000.0,
                    hasMedia = true,
                    isInBackground = false,
                    supportsSpeedAdjustment = true,
                    selfName = "me",
                    followerInControlledRoom = follower,
                    prefs = SyncPrefs(rewind = true, fastForward = true, slowdown = true, dontSlowWithMe = false),
                    messageAge = delay + ageError(i),
                ),
            )
            state = outcome.state
            actions += outcome.actions
            for (action in outcome.actions) when (action) {
                is SyncAction.Nudge -> rate = action.rate
                is SyncAction.SlowDown -> rate = SLOWDOWN_RATE
                SyncAction.RestoreSpeed -> rate = 1.0
                is SyncAction.SomeoneBehind -> local = action.toSeconds
                is SyncAction.SomeoneSeeked -> local = action.toSeconds
                is SyncAction.SomeoneFastForwarded -> local = action.toSeconds
                else -> Unit
            }
            rates += rate
            errors += local - room
        }

        val nudges get() = actions.filterIsInstance<SyncAction.Nudge>()
        val seeks get() = actions.filter { it is SyncAction.SomeoneBehind || it is SyncAction.SomeoneFastForwarded || it is SyncAction.SomeoneSeeked }
    }

    private fun noise(amplitude: Double, seed: Int): (Int) -> Double {
        val random = Random(seed)
        return { (random.nextDouble() * 2 - 1) * amplitude }
    }

    /** A player that runs 0.2 percent fast is held near the room by nudges alone. */
    @Test
    fun aSlowDriftIsCorrectedWithoutASeek() {
        val session = Session(drift = 1.002, ageError = noise(0.03, seed = 1))
        session.run(seconds = 1200)
        assertEquals(emptyList(), session.seeks, "No seek")
        assertTrue(session.actions.none { it is SyncAction.SlowDown }, "No step of the ladder")
        assertTrue(session.rates.all { it in (1 - NUDGE_RATE)..(1 + NUDGE_RATE) }, "The speed stays within half a percent")
        val settled = session.errors.drop(120)
        assertTrue(settled.all { abs(it) < 0.25 }, "The gap stays small: worst ${settled.maxOf { abs(it) }}s")
        assertTrue(settled.map { abs(it) }.average() < 0.12, "The gap stays near zero: mean ${settled.map { abs(it) }.average()}s")
        assertTrue(session.nudges.size < 60, "Few speed changes in 20 minutes: ${session.nudges.size}")
    }

    /** Network noise on its own never moves the speed, not even a message now and then that is 250 ms off. */
    @Test
    fun noiseAloneNeverMovesTheRate() {
        val small = noise(0.03, seed = 2)
        val random = Random(4)
        val session = Session(drift = 1.0, ageError = { i -> small(i) + if (random.nextInt(100) < 3) listOf(-0.25, 0.25).random(random) else 0.0 })
        session.run(seconds = 1200)
        assertEquals(emptyList(), session.nudges)
    }

    /** In a normal room the room waits for its slowest member, so a slow player is not sped up. */
    @Test
    fun aSlowPlayerInANormalRoomIsNotSpedUp() {
        val session = Session(drift = 0.998)
        session.run(seconds = 300)
        assertEquals(emptyList(), session.nudges)
    }

    /** A follower in a managed room catches up by itself, with a nudge instead of a jump. */
    @Test
    fun aFollowerCatchesUpWithANudge() {
        val session = Session(drift = 0.998, follower = true, ageError = noise(0.03, seed = 3))
        session.run(seconds = 1200)
        assertEquals(emptyList(), session.seeks, "No fast-forward")
        assertTrue(session.rates.any { it > 1.0 }, "The speed goes up a nudge")
        assertTrue(session.errors.drop(120).all { abs(it) < 0.25 })
    }

    /** A real jump is still the ladder's: the controller does not take on five seconds. */
    @Test
    fun aLargeJumpStillSeeks() {
        val session = Session(drift = 1.0, start = 5.0)
        session.run(seconds = 10)
        assertEquals(1, session.actions.count { it is SyncAction.SomeoneBehind }, "The ladder rewinds")
        // As in Syncplay, the same message also slows down for a second, which leaves 50 ms.
        assertTrue(abs(session.errors.last()) < 0.1, "The gap after the rewind: ${session.errors.last()}s")
        assertEquals(emptyList(), session.nudges, "The rewind left nothing to nudge")
    }

    /** A seek starts the controller over, and a running nudge ends with it. */
    @Test
    fun aSeekEndsANudge() {
        val session = Session(drift = 1.0, start = 0.4)
        session.run(seconds = 5)
        assertEquals(1.0 - NUDGE_RATE, session.rate, "A 400 ms lead is nudged")
        val before = session.actions.size
        session.run(seconds = 1, doSeekAt = 0)
        val after = session.actions.drop(before)
        assertTrue(after.first() is SyncAction.SomeoneSeeked, "$after")
        assertEquals(SyncAction.Nudge(1.0), after.last(), "The nudge ends after the seek: $after")
        assertEquals(0, session.state.nudgeLevel)
    }
}
