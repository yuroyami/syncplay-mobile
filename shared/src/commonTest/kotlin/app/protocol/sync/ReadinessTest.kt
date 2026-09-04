package app.protocol.sync

import app.player.models.MediaFile
import app.protocol.models.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Who the room is waiting for, and whether it may start on its own. */
class ReadinessTest {

    private fun user(name: String, ready: Boolean, hasFile: Boolean = true) = User(
        name = name,
        readiness = ready,
        file = if (hasFile) MediaFile().apply { fileName = "movie.mkv" } else null,
        isController = false,
    )

    private fun summary(vararg users: User) = summariseReadiness(users.toList(), selfName = "me")

    // ---- reading the roster ----

    @Test
    fun alone_when_nobody_else_has_a_file() {
        val s = summary(user("me", true), user("alice", true, hasFile = false))
        assertTrue(s.alone)
        assertTrue(s.everyoneReady, "there is nobody to wait for")
        assertEquals(1, s.participantCount)
    }

    @Test
    fun someone_with_no_file_is_neither_ready_nor_unready() {
        val s = summary(user("me", true), user("alice", false, hasFile = false), user("bob", true))
        assertEquals(listOf("bob"), s.peersWithFile)
        assertTrue(s.everyoneReady, "alice has nothing open, so she is not being waited on")
    }

    @Test
    fun the_unready_are_named_in_roster_order() {
        val s = summary(user("me", true), user("alice", false), user("bob", true), user("carol", false))
        assertEquals(listOf("alice", "carol"), s.notReady)
        assertFalse(s.everyoneReady)
        assertEquals(4, s.participantCount, "three peers with a file, plus us")
    }

    @Test
    fun our_own_readiness_is_never_something_we_wait_on() {
        val s = summary(user("me", false), user("alice", true))
        assertEquals(emptyList(), s.notReady)
        assertTrue(s.everyoneReady)
    }

    // ---- the countdown ----

    private fun countdown(
        autoplay: Boolean = true,
        paused: Boolean = true,
        canControl: Boolean = true,
        selfReady: Boolean = true,
        summary: ReadinessSummary = summary(user("me", true), user("alice", true)),
    ) = shouldCountDown(autoplay, paused, canControl, selfReady, summary)

    @Test
    fun a_paused_room_where_everyone_is_ready_counts_down() {
        assertTrue(countdown())
    }

    @Test
    fun autoplay_off_never_counts_down() {
        assertFalse(countdown(autoplay = false))
    }

    @Test
    fun a_room_that_is_already_playing_has_nothing_to_count_down_to() {
        assertFalse(countdown(paused = false))
    }

    @Test
    fun a_follower_in_a_controlled_room_cannot_start_the_room() {
        assertFalse(countdown(canControl = false))
    }

    @Test
    fun we_do_not_start_a_room_we_are_not_ready_for_ourselves() {
        assertFalse(countdown(selfReady = false))
    }

    @Test
    fun someone_still_unready_stops_the_countdown() {
        assertFalse(countdown(summary = summary(user("me", true), user("alice", false))))
    }

    @Test
    fun a_room_of_one_never_counts_down() {
        assertFalse(
            countdown(summary = summary(user("me", true))),
            "there is nothing to synchronise with",
        )
    }

    @Test
    fun the_countdown_length_matches_the_reference_client() {
        assertEquals(3, AUTOPLAY_COUNTDOWN_SECONDS)
    }
}
