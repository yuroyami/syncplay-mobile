package app.utils

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The user can export the log from settings and hand it to a stranger. A service key reaches the
 * log through too many paths to find one at a time, so [redactSecrets] masks the known secrets.
 */
class LogRedactionTest {

    @Test
    fun `known secrets are masked wherever they appear`() {
        val out = redactSecrets(
            "GET /api/v1/ABCDEFGH12345678/search?x=1 key=ABCDEFGH12345678",
            listOf("ABCDEFGH12345678"),
        )
        assertEquals("GET /api/v1/***/search?x=1 key=***", out)
    }

    @Test
    fun `short or blank secrets are ignored so nothing common is masked`() {
        assertEquals("abc", redactSecrets("abc", listOf("", "a")))
    }

    @Test
    fun `text with no secret in it is untouched`() {
        assertEquals("nothing to see", redactSecrets("nothing to see", listOf("ABCDEFGH12345678")))
    }
}
