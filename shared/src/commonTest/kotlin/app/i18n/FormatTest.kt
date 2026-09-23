package app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The generated strings of every language pass their parameters through [format], so a bug in
 * [format] mangles the text in every language at once.
 */
class FormatTest {

    @Test
    fun `placeholders are filled left to right`() {
        assertEquals("bob jumped from 0:10 to 2:00", "%s jumped from %s to %s".format("bob", "0:10", "2:00"))
        assertEquals("Running on port 8999", "Running on port %d".format(8999))
    }

    @Test
    fun `a lone percent is kept and a doubled one collapses`() {
        assertEquals("Subtitle position (%)", "Subtitle position (%)".format())
        assertEquals("50% of bob", "%d%% of %s".format(50, "bob"))
    }

    @Test
    fun `a placeholder with no argument left is kept as written`() {
        assertEquals("bob and %s", "%s and %s".format("bob"))
    }

    @Test
    fun `extra arguments are ignored`() {
        assertEquals("bob", "%s".format("bob", "unused"))
    }

    @Test
    fun `a string with no placeholders is returned unchanged`() {
        assertEquals("Server", "Server".format("bob"))
    }
}
