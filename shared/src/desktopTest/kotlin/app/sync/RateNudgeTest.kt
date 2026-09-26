package app.sync

import app.protocol.sync.NUDGE_RATE
import app.protocol.sync.SLOWDOWN_RATE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The rate controller's wiring, from the room's decision to the engine, with two real clients. */
class RateNudgeTest {

    /** The room keeps the whole decision state between messages, the controller's part included. */
    @Test
    fun theDecisionStateSurvivesFromOneMessageToTheNext() = TwoClientRoom().use { room ->
        val protocol = room.alice.viewmodel.protocol
        val kept = protocol.syncState.copy(speedChanged = true, nudgeLevel = -1, smoothedDiff = 0.2)
        protocol.syncState = kept
        assertEquals(kept, protocol.syncState)
    }

    /** A 400 ms lead gets a silent half-percent slowdown, not a seek, and the speed then comes back to normal. */
    @Test
    fun aSmallLeadIsNudgedNotSeeked() = TwoClientRoom().use { room ->
        room.joinAndLoad()
        room.alice.play()
        room.waitUntil("both play") { room.alice.player.playing && room.bob.player.playing }
        val seeksBefore = room.alice.player.seeks.toList()

        room.alice.player.jumpBy(400)
        room.waitUntil("alice slows by a nudge") { room.alice.player.speeds.lastOrNull() == 1.0 - NUDGE_RATE }
        room.alice.player.jumpBy(-400)
        room.waitUntil("alice is back at normal speed", timeoutMs = 15_000) { room.alice.player.speeds.lastOrNull() == 1.0 }

        assertEquals(seeksBefore, room.alice.player.seeks.toList(), "No seek")
        assertTrue(SLOWDOWN_RATE !in room.alice.player.speeds, "No step of the ladder: ${room.alice.player.speeds}")
        assertTrue(room.alice.viewmodel.notices.items.none { it.text.startsWith("Slowing down") }, "The nudge is silent")
    }
}
