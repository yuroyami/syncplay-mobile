package app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where a file was left. The policy matters more than the storage: offering to resume something
 * the viewer barely started, or already finished, is worse than not offering at all.
 */
class ResumePositionsTest {

    private fun point(
        name: String = "movie.mkv",
        positionMs: Long = 600_000,
        durationMs: Long = 7_200_000,
        at: Long = 1_000,
    ) = ResumePoint(name, positionMs, durationMs, at)

    @Test
    fun a_file_watched_well_into_it_is_worth_remembering() {
        assertTrue(worthRemembering(point()))
    }

    @Test
    fun the_first_minute_is_not_worth_remembering() {
        assertFalse(worthRemembering(point(positionMs = 59_000)))
        assertTrue(worthRemembering(point(positionMs = 61_000)))
    }

    @Test
    fun the_last_stretch_counts_as_finished() {
        assertFalse(worthRemembering(point(positionMs = 7_150_000, durationMs = 7_200_000)))
        assertTrue(worthRemembering(point(positionMs = 7_000_000, durationMs = 7_200_000)))
    }

    @Test
    fun an_unknown_length_trusts_the_position() {
        assertTrue(worthRemembering(point(positionMs = 600_000, durationMs = 0)))
        assertFalse(worthRemembering(point(positionMs = 10_000, durationMs = 0)))
    }

    @Test
    fun a_file_is_stored_once_and_the_newest_point_wins() {
        val first = withResumePoint(emptyList(), point(positionMs = 600_000, at = 1))
        val second = withResumePoint(first, point(positionMs = 900_000, at = 2))
        assertEquals(1, second.size)
        assertEquals(900_000, second.single().positionMs)
    }

    @Test
    fun a_point_that_would_never_be_offered_is_not_stored_and_clears_an_old_one() {
        val watched = withResumePoint(emptyList(), point(positionMs = 600_000, at = 1))
        val finished = withResumePoint(watched, point(positionMs = 7_190_000, at = 2))
        assertTrue(finished.isEmpty(), "finishing a file should forget it, not resume it at the credits")
    }

    @Test
    fun the_store_is_bounded_and_forgets_the_least_recent() {
        var store = emptyList<ResumePoint>()
        for (i in 1..(MAX_RESUME_POINTS + 10)) {
            store = withResumePoint(store, point(name = "file$i.mkv", at = i.toLong()))
        }
        assertEquals(MAX_RESUME_POINTS, store.size)
        assertTrue(store.none { it.fileName == "file1.mkv" }, "the oldest fell out")
        assertTrue(store.any { it.fileName == "file60.mkv" }, "the newest is kept")
    }

    @Test
    fun a_round_trip_through_storage_keeps_everything() {
        val store = withResumePoint(emptyList(), point())
        assertEquals(store, decodeResumePoints(encodeResumePoints(store)))
    }

    @Test
    fun a_store_that_will_not_parse_is_an_empty_store_not_an_exception() {
        assertEquals(emptyList(), decodeResumePoints("not json at all"))
        assertEquals(emptyList(), decodeResumePoints(""))
        assertEquals(emptyList(), decodeResumePoints("""{"unexpected":"shape"}"""))
    }

    @Test
    fun looking_up_a_file_nobody_watched_finds_nothing() {
        val store = withResumePoint(emptyList(), point(name = "a.mkv"))
        assertNull(resumePointFor(store, "b.mkv"))
        assertEquals(600_000, resumePointFor(store, "a.mkv")?.positionMs)
    }
}
