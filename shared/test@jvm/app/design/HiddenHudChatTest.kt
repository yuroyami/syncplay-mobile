package app.design

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.room.models.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A chat line that arrives while the HUD (the controls over the video) is hidden shows at the place
 * where the chat list shows it. When the HUD comes back, the line does not move.
 */
class HiddenHudChatTest {
    /** The chat rows that say [text]. A row carries its whole line as its spoken description. */
    private fun List<SemanticsNode>.rowsSaying(text: String): List<SemanticsNode> =
        filter { node -> node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { text in it } == true }

    private fun RoomRig.Room.send(sender: String, content: String, mine: Boolean = false) {
        viewmodel.session.messageSequence.value = viewmodel.session.messageSequence.value +
            Message(sender = sender, content = content, isMainUser = mine, timestamp = DesignHarness.CHAT_TIME)
    }

    @Test
    fun aLineThatArrivesWhileHiddenKeepsItsPlaceWhenTheHudComesBack() =
        RoomRig.drive(solo = false, television = false, withVideo = true) {
            send("Sam", "ok, starting from the cold open")
            send("Ari", "same file? mine look fine", mine = true)
            driver.frames(20)
            ui.visibleHUD.value = false
            driver.frames(30)

            send("Mia", "brb, popcorn")
            driver.frames(20)
            val rows = allNodes().rowsSaying("brb, popcorn")
            assertEquals(2, rows.size, "The list has the line, and the hidden HUD shows it once more over the video")
            val (first, second) = rows.map(::boundsOf)
            assertEquals(first, second, "The shown line sits exactly on its place in the list")
            assertTrue(spokenNodes().rowsSaying("brb, popcorn").size <= 1, "A screen reader hears the line once")

            ui.visibleHUD.value = true
            driver.frames(30)
            val back = allNodes().rowsSaying("brb, popcorn")
            assertEquals(1, back.size, "With the HUD back, only the list shows the line")
            assertEquals(first, boundsOf(back.single()), "The line did not move")
        }

    @Test
    fun aGroupedLineShowsTheNameOfItsGroupAtItsPlace() =
        RoomRig.drive(solo = false, television = false, withVideo = true) {
            send("Mia", "starting now")
            driver.frames(20)
            ui.visibleHUD.value = false
            driver.frames(30)

            send("Mia", "brb, popcorn")
            driver.frames(20)
            val line = allNodes().rowsSaying("brb, popcorn")
            assertEquals(2, line.size, "The new line shows over the video")
            assertEquals(boundsOf(line[0]), boundsOf(line[1]), "The new line sits on its place in the list")
            val head = allNodes().rowsSaying("starting now")
            assertEquals(2, head.size, "The first line of the group shows its name, without its text")
            assertEquals(boundsOf(head[0]), boundsOf(head[1]), "The name sits on its place in the list")
        }

    @Test
    fun rendersTheShownLineOverABrightPicture() =
        RoomRig.drive(solo = false, television = false, withVideo = true, picture = { m -> TestScenes.BrightScene(m) }) {
            send("Sam", "ok, starting from the cold open")
            send("Ari", "same file? mine look fine", mine = true)
            driver.frames(20)
            ui.visibleHUD.value = false
            driver.frames(30)
            send("Mia", "brb, popcorn")
            send("Sam", "same here")
            driver.frames(20)
            driver.save("hidden-hud-chat-1-hidden")
            ui.visibleHUD.value = true
            driver.frames(5)
            driver.save("hidden-hud-chat-2-coming-back")
            driver.frames(30)
            driver.save("hidden-hud-chat-3-back")
        }
}
