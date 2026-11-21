package app.room.ui.chat

import androidx.compose.ui.graphics.Color
import app.room.models.Message
import kotlin.test.Test
import kotlin.test.assertEquals

/** The portrait chat gives each group of lines one band, and the groups take turns between the ground and the lighter band. */
class ChatBandsTest {
    private val ground = Color.Transparent

    private fun said(sender: String, atSeconds: Int) = Message(sender = sender, content = "hi", epochMs = atSeconds * 1_000L)

    private fun event(atSeconds: Int) = Message(content = "Ann paused at 12:34", epochMs = atSeconds * 1_000L)

    @Test
    fun aGroupSharesOneBandAndTheGroupsTakeTurns() {
        val lines = listOf(said("Ann", 0), said("Ann", 10), said("Bob", 20), said("Ann", 30))
        assertEquals(listOf(ground, ground, ChatBand, ground), chatBands(lines))
    }

    @Test
    fun anEventStandsInItsOwnBand() {
        val lines = listOf(said("Ann", 0), event(5), event(6), said("Ann", 7))
        assertEquals(listOf(ground, ChatBand, ground, ChatBand), chatBands(lines))
    }

    @Test
    fun aMinuteOfSilenceStartsANewGroup() {
        val lines = listOf(said("Ann", 0), said("Ann", 59), said("Ann", 119))
        assertEquals(listOf(ground, ground, ChatBand), chatBands(lines))
    }

    @Test
    fun anEmptyChatHasNoBands() {
        assertEquals(emptyList(), chatBands(emptyList()))
    }
}
