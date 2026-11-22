package app.design

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.preferences.Preferences.MSG_BG_OPACITY
import app.preferences.flow
import app.preferences.set
import app.room.models.Message
import app.room.ui.chat.PIP_LINES
import app.utils.platformCallback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The room in picture-in-picture (PiP): the small window shows the video and the newest lines from
 * other people, and the platform hears each play state that the engine reports, for the play and
 * pause key of the window.
 */
class PipChatTest {
    /** The chat rows that say [text]. A row carries its whole line as its spoken description. */
    private fun List<SemanticsNode>.rowsSaying(text: String): List<SemanticsNode> =
        filter { node -> node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { text in it } == true }

    private fun RoomRig.Room.send(sender: String?, content: String, mine: Boolean = false) {
        viewmodel.session.messageSequence.value = viewmodel.session.messageSequence.value +
            Message(sender = sender, content = content, isMainUser = mine, timestamp = DesignHarness.CHAT_TIME)
    }

    private fun pipRoom(picture: Boolean = false, drive: RoomRig.Room.() -> Unit) = RoomRig.drive(
        solo = false,
        television = false,
        withVideo = true,
        widthDp = 240,
        heightDp = 135,
        picture = if (picture) ({ m -> TestScenes.BrightScene(m) }) else null,
        drive = drive,
    )

    @Test
    fun aNewLineFromAnotherPersonShowsInPictureInPicture() = pipRoom {
        driver.frames(10)
        // Home sends the room to PiP without hiding its controls first, so the HUD state stays on.
        ui.visibleHUD.value = true
        ui.hasEnteredPipMode.value = true
        driver.frames(10)

        send("Mia", "brb, popcorn")
        send(null, "Sam paused")
        driver.frames(20)
        assertEquals(1, allNodes().rowsSaying("brb, popcorn").size, "The PiP window shows the chat line")
        assertEquals(1, allNodes().rowsSaying("Sam paused").size, "The PiP window shows the event line")
    }

    @Test
    fun aLineOfMyOwnDoesNotShowInPictureInPicture() = pipRoom {
        ui.hasEnteredPipMode.value = true
        driver.frames(10)
        send("tester", "on my way", mine = true)
        driver.frames(20)
        assertTrue(allNodes().rowsSaying("on my way").isEmpty(), "I know what I wrote")
    }

    @Test
    fun pictureInPictureShowsOnlyTheNewestLines() = pipRoom {
        ui.hasEnteredPipMode.value = true
        driver.frames(10)
        val lines = (1..PIP_LINES + 1).map { "line number $it" }
        lines.forEach { send("Mia", it) }
        driver.frames(20)
        assertTrue(allNodes().rowsSaying(lines.first()).isEmpty(), "The oldest line gave way")
        lines.drop(1).forEach { assertEquals(1, allNodes().rowsSaying(it).size, "\"$it\" shows") }
    }

    @Test
    fun theLinesStayInsideTheSmallWindow() = pipRoom {
        ui.hasEnteredPipMode.value = true
        driver.frames(10)
        send("Mia", "brb, popcorn")
        driver.frames(20)
        val row = boundsOf(allNodes().rowsSaying("brb, popcorn").single())
        val window = boundsOf(allNodes().first())
        assertTrue(row.left >= window.left && row.right <= window.right, "The line is inside the window: $row in $window")
        assertTrue(row.top >= window.top && row.bottom <= window.bottom, "The line is inside the window: $row in $window")
        assertTrue(row.bottom > window.bottom - window.height / 4, "The line sits at the bottom of the window: $row in $window")
    }

    @Test
    fun thePlatformHearsEachPlayStateThatTheEngineReports() = pipRoom {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        platformCallback = RoomRig.inertPlatform(pictureInPicture = true, calls = calls)
        fun lastPlayback() = synchronized(calls) { calls.lastOrNull { it.startsWith("onPlayback") } }

        // Another person starts the room: nothing on this device asked for it.
        DesignHarness.onUiThread { viewmodel.playerManager.isNowPlaying.value = true }
        driver.frames(10)
        assertEquals("onPlayback(false)", lastPlayback(), "The platform hears the play")
        DesignHarness.onUiThread { viewmodel.playerManager.isNowPlaying.value = false }
        driver.frames(10)
        assertEquals("onPlayback(true)", lastPlayback(), "The platform hears the pause")
    }

    @Test
    fun rendersTheLinesOverABrightPicture() = pipRoom(picture = true) {
        ui.hasEnteredPipMode.value = true
        driver.frames(10)
        send(null, "Sam jumped from 00:01 to 03:07")
        send("Mia", "brb, popcorn")
        driver.frames(20)
        driver.save("pip-chat-240dp")
    }

    @Test
    fun rendersThePlatesOfTheLandscapeChatOverABrightPicture() {
        DesignHarness.initDatastore()
        for (opacity in listOf(0, 150)) {
            runBlocking {
                MSG_BG_OPACITY.set(opacity)
                withTimeout(2_000) { MSG_BG_OPACITY.flow().first { it == opacity } }
            }
            try {
                RoomRig.drive(solo = false, television = false, withVideo = true, picture = { m -> TestScenes.BrightScene(m) }) {
                    send(null, "Successfully connected to server")
                    send("Sam", "ok, starting from the cold open")
                    send("Sam", "one sec")
                    send(null, "Sam jumped from 00:01 to 03:07")
                    send("Ari", "same file? mine look fine", mine = true)
                    driver.frames(30)
                    driver.save("chat-over-bright-plate-$opacity")
                }
            } finally {
                runBlocking { MSG_BG_OPACITY.set(MSG_BG_OPACITY.default) }
            }
        }
    }
}
