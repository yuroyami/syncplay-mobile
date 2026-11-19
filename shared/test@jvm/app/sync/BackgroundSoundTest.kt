package app.sync

import app.design.DesignHarness
import app.preferences.Preferences.KEEP_SOUND_AWAY
import app.preferences.flow
import app.preferences.set
import app.room.HoldReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Background sound (#125): a phone that leaves the screen keeps playing the room's sound, as at a
 * party where each phone feeds its own headphones and one shared screen shows the picture.
 *
 * Alice is the phone in the pocket and Bob is the shared screen. While Alice is away, she follows
 * the room and never moves it: no pause, no seek and no stall of hers may reach Bob.
 */
class BackgroundSoundTest {

    private fun withSetting(on: Boolean, block: () -> Unit) {
        setSetting(on)
        try {
            block()
        } finally {
            setSetting(KEEP_SOUND_AWAY.default)
        }
    }

    private fun setSetting(on: Boolean) = runBlocking {
        DesignHarness.initDatastore()
        KEEP_SOUND_AWAY.set(on)
        withTimeout(2_000) { KEEP_SOUND_AWAY.flow().first { it == on } }
    }

    private fun TwoClientRoom.bothPlayThenAliceLeaves() {
        joinAndLoad()
        alice.play()
        waitUntil("both play") { alice.player.playing && bob.player.playing }
        runOnMain { alice.viewmodel.uiState.onLifecycleStop() }
        waitUntil("alice stops decoding video") { alice.player.videoSuspensions.lastOrNull() == true }
    }

    private fun TwoClientRoom.aliceComesBack() {
        runOnMain { alice.viewmodel.uiState.onLifecycleStart() }
        waitUntil("alice decodes video again") { alice.player.videoSuspensions.lastOrNull() == false }
    }

    @Test
    fun aPhoneAwayKeepsPlayingAndFollowsPausePlayAndSeek() = withSetting(true) {
        TwoClientRoom(backgroundSound = true).use { room ->
            room.bothPlayThenAliceLeaves()
            assertTrue(room.alice.viewmodel.uiState.isListeningAway)
            assertTrue("onListeningAway(true)" in room.platformCalls, "the platform keeps the phone awake")
            Thread.sleep(1_000)
            assertTrue(room.alice.player.playing, "the sound keeps playing away from the screen")

            room.bob.pause()
            room.waitUntil("alice pauses with the room") { !room.alice.player.playing }
            room.bob.play()
            room.waitUntil("alice plays with the room") { room.alice.player.playing }
            room.bob.seek(200_000)
            room.waitUntil("alice follows the seek") { room.alice.positionMs() in 199_000..203_000 }

            room.aliceComesBack()
            assertTrue(room.alice.player.playing, "coming back changes nothing in a room that plays")
            assertTrue("onListeningAway(false)" in room.platformCalls)
        }
    }

    @Test
    fun aPhoneAwayNeverPullsTheRoomBack() = withSetting(true) {
        TwoClientRoom(backgroundSound = true).use { room ->
            room.bothPlayThenAliceLeaves()
            val bobSeeks = room.bob.player.seeks.size
            // A stall in the pocket leaves Alice five seconds behind.
            room.alice.player.jumpBy(-5_000)
            room.waitUntil("alice catches up by herself", timeoutMs = 10_000) { room.gapMs() < 1_000 }
            assertEquals(bobSeeks, room.bob.player.seeks.size, "the shared screen never moved")
            assertTrue(room.bob.player.speeds.none { it < 1.0 }, "the shared screen never slowed down")
        }
    }

    @Test
    fun aPhoneAwaySendsNoCommandToTheRoom() = withSetting(true) {
        TwoClientRoom(backgroundSound = true).use { room ->
            room.bothPlayThenAliceLeaves()
            val bobSeeks = room.bob.player.seeks.size
            room.alice.pause()
            room.alice.seek(300_000)
            Thread.sleep(1_500)
            assertTrue(room.bob.player.playing, "a command from the pocket never pauses the room")
            assertEquals(bobSeeks, room.bob.player.seeks.size, "a command from the pocket never seeks the room")
            assertTrue(room.alice.player.playing, "the phone itself keeps following the room")
        }
    }

    @Test
    fun anEngineStopAwayStaysOnThePhoneAndHoldsIt() = withSetting(true) {
        TwoClientRoom(backgroundSound = true).use { room ->
            room.bothPlayThenAliceLeaves()
            room.alice.player.stopByItself(marked = false)
            room.waitUntil("alice holds") { room.alice.viewmodel.uiState.holds.has(HoldReason.ListeningEnded) }
            Thread.sleep(1_000)
            assertTrue(room.bob.player.playing, "nobody else pauses")
            assertFalse(room.alice.player.playing)

            room.aliceComesBack()
            room.waitUntil("alice plays with the room again") { room.alice.player.playing && room.gapMs() < 1_000 }
        }
    }

    @Test
    fun aRoomThatStaysPausedEndsListeningUntilThePhoneComesBack() = withSetting(true) {
        TwoClientRoom(backgroundSound = true).use { room ->
            room.alice.viewmodel.uiState.pauseGrace = 500.milliseconds
            room.bothPlayThenAliceLeaves()
            room.bob.pause()
            room.waitUntil("listening ends") { room.alice.viewmodel.uiState.holds.has(HoldReason.ListeningEnded) }
            room.bob.play()
            Thread.sleep(1_000)
            assertFalse(room.alice.player.playing, "after the grace, the room cannot start the phone again")

            room.aliceComesBack()
            room.waitUntil("alice plays with the room again") { room.alice.player.playing && room.gapMs() < 1_000 }
        }
    }

    @Test
    fun aLostConnectionHoldsAndTheReconnectBringsTheSoundBack() = withSetting(true) {
        TwoClientRoom(backgroundSound = true).use { room ->
            room.alice.viewmodel.uiState.connectionGrace = 0.seconds
            room.bothPlayThenAliceLeaves()
            room.platformCalls.clear()
            (room.alice.viewmodel.networkManager as LoopbackTransport).sever()
            room.waitUntil("alice holds, reconnects and listens again", timeoutMs = 20_000) {
                room.platformCalls.indexOf("onListeningAway(false)").let { it >= 0 && "onListeningAway(true)" in room.platformCalls.drop(it) }
            }
            room.waitUntil("alice plays with the room again", timeoutMs = 10_000) { room.alice.player.playing && room.gapMs() < 1_000 }
            assertTrue(room.bob.player.playing)
        }
    }

    @Test
    fun theSettingOffKeepsTodaysHold() = withSetting(false) {
        TwoClientRoom(backgroundSound = true).use { room ->
            room.joinAndLoad()
            room.alice.play()
            room.waitUntil("both play") { room.alice.player.playing && room.bob.player.playing }
            runOnMain { room.alice.viewmodel.uiState.onLifecycleStop() }
            room.waitUntil("alice holds") { !room.alice.player.playing }
            assertTrue(room.alice.viewmodel.uiState.holds.has(HoldReason.LeftScreen))
            assertTrue(room.alice.player.videoSuspensions.isEmpty(), "a held phone leaves the video alone")
            assertTrue(room.bob.player.playing)
        }
    }

    @Test
    fun thePlatformHearsWhenTheRoomCouldKeepItsSound() = withSetting(true) {
        TwoClientRoom(backgroundSound = true).use { room ->
            room.joinAndLoad()
            room.alice.viewmodel.uiState.watchBackgroundSoundReadiness()
            room.waitUntil("the platform may start its service") { "backgroundSoundReady(true)" in room.platformCalls }
            runOnMain { room.alice.viewmodel.uiState.onLifecycleStop() }
            room.waitUntil("the room left the screen") { "backgroundSoundReady(false)" in room.platformCalls }
        }
    }
}
