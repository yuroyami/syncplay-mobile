package app.room.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import app.room.models.isolated
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Event lines colour a person's name only where `isolated()` wrapped the whole name. */
class EventTextTest {
    private val self = Color(0xFF8E5CF7)
    private val friend = Color(0xFF3DDC84)

    /** Each coloured run as the text it covers and its colour. */
    private fun AnnotatedString.colouredRuns() = spanStyles.map { text.substring(it.start, it.end) to it.item.color }

    @Test
    fun aPeerTakesTheFriendColourAndTheAppUserTheSelfColour() {
        val paused = eventText("Christopher".isolated() + " paused at 12:34", mapOf("Christopher" to false), self, friend)
        assertEquals(listOf("Christopher" to friend), paused.colouredRuns())
        val seeked = eventText("Alexandra".isolated() + " jumped from 01:00 to 02:00", mapOf("Alexandra" to true), self, friend)
        assertEquals(listOf("Alexandra" to self), seeked.colouredRuns())
    }

    @Test
    fun bothPeopleInAReadinessLineTakeTheirOwnColour() {
        val line = eventText(
            "Bob".isolated() + " was set as ready by " + "Alice".isolated(),
            mapOf("Bob" to true, "Alice" to false), self, friend,
        )
        assertEquals(listOf("Bob" to self, "Alice" to friend), line.colouredRuns())
    }

    @Test
    fun aNameIsFoundWhereverTheTranslationPutsIt() {
        val line = eventText("Rewound due to time difference with " + "Dana".isolated(), mapOf("Dana" to false), self, friend)
        assertEquals(listOf("Dana" to friend), line.colouredRuns())
    }

    @Test
    fun onlyWholeIsolatedNamesAreColoured() {
        val playing = eventText("Ann".isolated() + " is playing '" + "Anna.mkv".isolated() + "'", mapOf("Ann" to false), self, friend)
        assertEquals(listOf("Ann" to friend), playing.colouredRuns())
        // A name that was never isolated is left alone rather than guessed at.
        assertTrue(eventText("Ann paused", mapOf("Ann" to false), self, friend).spanStyles.isEmpty())
    }

    @Test
    fun aLineThatNamesNobodyHasNoColour() {
        assertTrue(eventText("Connected to the server", emptyMap(), self, friend).spanStyles.isEmpty())
    }
}
