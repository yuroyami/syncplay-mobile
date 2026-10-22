package app.utils

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The log keeps one day in part files of a capped size, and all of them under a total cap. */
class LogRotationTest {

    private val dir = createTempDirectory("synkplay-logs").toFile()

    @AfterTest
    fun clean() {
        dir.deleteRecursively()
    }

    private fun write(name: String, bytes: Int) = File(dir, name).writeText("x".repeat(bytes))

    @Test
    fun partNamesParseInDayAndPartOrder() {
        assertEquals(LogPart("2026-09-26.log", "2026-09-26", 1), logPartOf("2026-09-26.log"))
        assertEquals(LogPart("2026-09-26.3.log", "2026-09-26", 3), logPartOf("2026-09-26.3.log"))
        assertNull(logPartOf("notes.txt"))
        assertNull(logPartOf("2026-09-26.old.log"))
    }

    @Test
    fun aFullDayStartsTheNextPart() {
        val path = dir.path
        assertEquals("$path/2026-09-26.log", logFileFor(path, "2026-09-26", 10, maxFileBytes = 100))
        write("2026-09-26.log", 95)
        assertEquals("$path/2026-09-26.2.log", logFileFor(path, "2026-09-26", 10, maxFileBytes = 100))
        write("2026-09-26.2.log", 20)
        assertEquals("$path/2026-09-26.2.log", logFileFor(path, "2026-09-26", 10, maxFileBytes = 100))
    }

    @Test
    fun theOldestPartsGoFirstPastTheTotalCap() {
        write("2026-09-24.log", 100)
        write("2026-09-25.log", 100)
        write("2026-09-25.2.log", 100)
        write("2026-09-26.log", 100)
        enforceTotalLogSize(dir.path, maxTotalBytes = 250)
        assertEquals(listOf("2026-09-25.2.log", "2026-09-26.log"), dir.list()!!.sorted())
    }
}
