package app.server

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The log of the server that the app hosts keeps its last 500 lines, so past 500 the list stops
 * growing. A reader that counts the lines it has seen would see the same count forever and show
 * nothing new, so [ServerLogCursor] keeps its place by sequence number.
 */
class ServerLogCursorTest {

    private fun entry(seq: Long) = ServerLogEntry(seq = seq, timestamp = seq, event = ServerLogEvent.ShuttingDown)

    @Test
    fun `every line past the cap is still delivered once`() {
        val cursor = ServerLogCursor()

        val first = (1L..500L).map(::entry)
        assertEquals(500, cursor.newSince(first).size)

        // 100 more lines arrive; the capped list is still 500 long.
        val rotated = (101L..600L).map(::entry)
        assertEquals((501L..600L).toList(), cursor.newSince(rotated).map { it.seq })

        // And nothing is delivered twice.
        assertEquals(0, cursor.newSince(rotated).size)
    }

    @Test
    fun `a fresh cursor takes everything it is given`() {
        assertEquals(3, ServerLogCursor().newSince((7L..9L).map(::entry)).size)
    }
}
