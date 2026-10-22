package app.room

import app.room.ui.rightcards.rosterFileSize
import kotlin.test.Test
import kotlin.test.assertEquals

class RosterFileSizeTest {
    @Test
    fun `unknown private and invalid sizes stay unknown`() {
        listOf("", "0", "-1", "deadbeefabcd", "NaN", "Infinity", "42.5").forEach { raw ->
            assertEquals("—", rosterFileSize(raw), raw)
        }
    }

    @Test
    fun `small known files retain exact byte counts`() {
        assertEquals("1 B", rosterFileSize("1"))
        assertEquals("999 B", rosterFileSize("999"))
    }

    @Test
    fun `decimal units stay readable without a redundant zero decimal`() {
        assertEquals("1 KB", rosterFileSize("1000"))
        assertEquals("1.5 KB", rosterFileSize("1500"))
        assertEquals("650 MB", rosterFileSize("650000000"))
        assertEquals("1.7 GB", rosterFileSize("1700000000"))
        assertEquals("1 TB", rosterFileSize("1000000000000"))
    }
}
