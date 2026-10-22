package app.sync

import app.player.ResumePoint
import app.player.encodeResumePoints
import app.preferences.Preferences.RESUME_PLAYBACK
import app.preferences.Preferences.RESUME_POSITIONS
import app.preferences.set
import app.room.RoomViewmodel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Each loaded file is announced once, whatever the engine knows about its duration. */
class FileAnnounceTest {

    @Test
    fun aLiveStreamWithNoDurationStillReachesTheRoom() = TwoClientRoom().use { room ->
        room.waitUntil("both clients connect") { room.alice.connected && room.bob.connected }
        room.alice.player.readyDurationMs = null
        runBlocking { room.alice.player.injectVideoURL(TwoClientRoom.CLIP) }
        room.waitUntil("bob sees alice's stream") {
            room.bob.viewmodel.session.userList.value.any { it.name == "alice" && it.file?.fileName == "clip.mp4" }
        }
    }

    @Test
    fun aDismissedOfferToContinueNeverComesBackForTheSameFile() = soloRoom { viewmodel, player ->
        runBlocking { player.injectVideoURL(TwoClientRoom.CLIP) }
        waitFor("the offer to continue") { viewmodel.resume.offer.value != null }
        viewmodel.resume.startOver()

        // A stream that refines its length reports it again, and the engine may report ready again.
        player.onEngineDurationChanged(TwoClientRoom.CLIP_LENGTH_MS + 5_000)
        player.onEngineFileReady(TwoClientRoom.CLIP_LENGTH_MS + 5_000)
        Thread.sleep(300)
        assertNull(viewmodel.resume.offer.value)
        assertEquals(TwoClientRoom.CLIP_LENGTH_MS + 5_000, viewmodel.playerManager.timeFullMillis.value)
    }

    @Test
    fun aNewFileClosesTheOfferOfTheLastOne() = soloRoom { viewmodel, player ->
        runBlocking { player.injectVideoURL(TwoClientRoom.CLIP) }
        waitFor("the offer to continue") { viewmodel.resume.offer.value != null }
        player.readyDurationMs = null
        runBlocking { player.injectVideoURL("https://example.com/other.mp4") }
        waitFor("the other file") { viewmodel.media?.fileName == "other.mp4" }
        assertNull(viewmodel.resume.offer.value)
    }

    /** A room with no server, watching alone, with a saved point two minutes into the clip. */
    private fun soloRoom(test: (RoomViewmodel, ClockPlayer) -> Unit) = withSoloRoom { viewmodel, player ->
        runBlocking {
            RESUME_PLAYBACK.set(true)
            RESUME_POSITIONS.set(encodeResumePoints(listOf(ResumePoint("clip.mp4", 120_000, TwoClientRoom.CLIP_LENGTH_MS, 1))))
        }
        try {
            test(viewmodel, player)
        } finally {
            runBlocking { RESUME_POSITIONS.set(RESUME_POSITIONS.default) }
        }
    }
}
