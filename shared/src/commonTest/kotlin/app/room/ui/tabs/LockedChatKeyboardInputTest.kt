package app.room.ui.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LockedChatKeyboardInputTest {

    @Test
    fun appendsTextWithoutExceedingTheServerLimit() {
        assertEquals(
            "hello",
            appendLockedChatText(draft = "hel", text = "lo world", maxLength = 5),
        )
    }

    @Test
    fun backspaceRemovesAWholeUnicodeCodePoint() {
        assertEquals(
            "a",
            removeLockedChatCharacter("a\uD83D\uDE00"),
        )
    }

    @Test
    fun sendRejectsBlankDraftsAndCapsThePayload() {
        assertNull(lockedChatMessageToSend("   ", maxLength = 150))
        assertEquals("hello", lockedChatMessageToSend("hello world", maxLength = 5))
    }
}
