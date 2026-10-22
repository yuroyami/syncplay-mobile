package app.sync

import app.protocol.ProtocolManager
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The room that paused itself: an engine that stopped on its own sent that stop to the room as a
 * pause, so everyone stopped. Each engine now marks such a stop as expected before it happens.
 * These tests hold the room's side of that contract, with two real clients and the app's own server.
 */
class EngineStopTest {

    // The collector reacts at once, and the watchdog repeats the check on every tick.
    private val oneWatchdogTick = ProtocolManager.WATCHDOG_INTERVAL_SECONDS * 1000 + 1000

    private fun TwoClientRoom.playing() {
        joinAndLoad()
        alice.play()
        waitUntil("both play") { alice.player.playing && bob.player.playing }
    }

    /** A stop the engine marks (a stream error, the next file opening) stays on this device. */
    @Test
    fun aMarkedEngineStopStaysLocal() = TwoClientRoom().use { room ->
        room.playing()
        room.alice.player.stopByItself(marked = true)
        Thread.sleep(oneWatchdogTick)
        assertTrue(room.bob.player.playing, "Bob still plays: the room heard nothing")
    }

    /** A stop the engine does not mark (audio focus loss) is a pause, and the room follows it. */
    @Test
    fun anUnmarkedEngineStopPausesTheRoom() = TwoClientRoom().use { room ->
        room.playing()
        room.alice.player.stopByItself(marked = false)
        room.waitUntil("bob pauses with alice") { !room.bob.player.playing }
    }

    /** A buffering stall is not a pause, and nothing reaches the room. */
    @Test
    fun aBufferingStallSendsNothing() = TwoClientRoom().use { room ->
        room.playing()
        room.alice.player.stall(true)
        Thread.sleep(oneWatchdogTick)
        assertTrue(room.bob.player.playing, "Bob still plays through alice's stall")
        assertTrue(room.alice.player.playing, "A stall does not stop alice either")
        room.alice.player.stall(false)
    }

    /** The real end of the file is the stop the room must hear: everyone pauses at the end. */
    @Test
    fun theEndOfTheFileReachesTheRoom() = TwoClientRoom().use { room ->
        room.playing()
        room.alice.player.reachEnd()
        room.waitUntil("bob pauses at alice's end") { !room.bob.player.playing }
        assertFalse(room.alice.player.playing)
    }
}
