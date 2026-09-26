package app.design

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import app.room.models.Message
import app.room.models.MessagePalette
import app.room.models.isolated
import app.room.ui.chat.EventLine
import app.room.ui.chat.MessageStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Room events over video: grey lines, people in their chat name colours, errors all red, outlines black. */
class ChatEventsGolden {
    private val paused = Message(content = PEER.isolated() + " paused at 12:34", people = mapOf(PEER to false))
    private val seeked = Message(content = SELF.isolated() + " jumped from 01:00 to 02:00", people = mapOf(SELF to true))
    private val readySetBy = Message(
        content = SELF.isolated() + " was set as ready by " + PEER.isolated(),
        people = mapOf(SELF to true, PEER to false),
    )
    private val connected = Message(content = "Successfully connected to server")
    private val refused = Message(
        content = PEER.isolated() + " failed to identify as a room operator",
        isError = true,
        people = mapOf(PEER to false),
    )
    private val events = listOf(paused, seeked, readySetBy, connected, refused)

    @Test
    fun eventsAreGreyAndNameTheirPeopleInChatColours() {
        val chat = MessagePalette()
        for (scale in listOf(1f, 1.3f)) {
            val result = DesignHarness.render("chat-events", widthDp = 272, heightDp = 300, fontScale = scale, overVideo = true) {
                Column { events.forEach { EventLine(it, chat, MessageStyle(10, outline = 2f, shadow = false, showTime = false)) } }
            }
            result.assertAllTextFits()
            val (outlines, fills) = result.textLayouts.partition { it.layoutInput.style.drawStyle is Stroke }

            assertEquals(events.size, outlines.size, "Every event draws one outline")
            assertTrue(outlines.all { it.layoutInput.text.spanStyles.isEmpty() }, "An outline must stay black under a coloured name")

            fun runsOf(message: Message): List<Pair<String, Color>> = fills.single { it.text() == message.content }.let { layout ->
                layout.layoutInput.text.spanStyles.map { layout.text().substring(it.start, it.end) to it.item.color }
            }
            assertEquals(listOf(PEER to chat.friendtagColor), runsOf(paused))
            assertEquals(listOf(SELF to chat.selftagColor), runsOf(seeked))
            assertEquals(listOf(SELF to chat.selftagColor, PEER to chat.friendtagColor), runsOf(readySetBy))
            assertEquals(emptyList(), runsOf(connected))
            assertEquals(emptyList(), runsOf(refused), "An error stays one colour")
        }
    }

    private fun TextLayoutResult.text() = layoutInput.text.text

    private companion object {
        const val SELF = "Alexandra_Morgan"
        const val PEER = "Christopher_Lee"
    }
}
