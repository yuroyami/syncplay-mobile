package app.room

import app.room.models.collapsedForChat
import kotlin.test.Test
import kotlin.test.assertEquals

/** Chat text keeps its words and drops the blank lines that pad the official server's notice. */
class MessageTextTest {

    @Test
    fun `blank lines and trailing spaces go, words stay`() {
        assertEquals(
            "Hello to Syncplay stuff idk,\nlol",
            "Hello to Syncplay stuff idk,\n \n\nlol".collapsedForChat(),
        )
    }

    @Test
    fun `windows line ends are handled`() {
        assertEquals("a\nb", "a\r\n\r\nb\r\n".collapsedForChat())
    }

    @Test
    fun `a one line message is unchanged`() {
        assertEquals("hi", "hi".collapsedForChat())
    }

    @Test
    fun `an all blank message becomes empty`() {
        assertEquals("", " \n\t\n".collapsedForChat())
    }

    @Test
    fun `leading spaces inside a line survive`() {
        assertEquals("  art\n  art", "  art\n\n  art".collapsedForChat())
    }
}
