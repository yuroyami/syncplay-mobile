package app.protocol.sync

import app.protocol.wire.IgnoringOnTheFlyData
import app.protocol.wire.PlaystateData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The sync algorithm, finally under test. Everything here drives [decideSync] directly: no
 * player, no socket, no clock, no preferences.
 */
class SyncDecisionTest {

    private val t0 = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    private fun ctx(
        playerSeconds: Double = 0.0,
        hasMedia: Boolean = true,
        background: Boolean = false,
        speed: Boolean = true,
        self: String = "me",
        follower: Boolean = false,
        prefs: SyncPrefs = SyncPrefs(rewind = true, fastForward = true, slowdown = true, dontSlowWithMe = false),
        age: Double = 0.0,
        now: Instant = t0,
    ) = SyncContext(
        now = now,
        playerPositionMs = playerSeconds * 1000.0,
        hasMedia = hasMedia,
        isInBackground = background,
        supportsSpeedAdjustment = speed,
        selfName = self,
        followerInControlledRoom = follower,
        prefs = prefs,
        messageAge = age,
    )

    private fun playstate(
        position: Double = 0.0,
        paused: Boolean = false,
        doSeek: Boolean? = null,
        setBy: String? = "peer",
    ) = PlaystateData(position = position, paused = paused, doSeek = doSeek, setBy = setBy)

    /** An anchor that already exists, so tests do not keep tripping the first-sync branch. */
    private fun anchored(
        paused: Boolean = false,
        speedChanged: Boolean = false,
        behindSince: Instant? = null,
    ) = SyncState(
        globalPaused = paused,
        lastGlobalUpdate = t0 - 10.seconds,
        speedChanged = speedChanged,
        behindFirstDetected = behindSince,
    )

    // ---- ignoringOnTheFly ----

    @Test
    fun a_server_counter_is_adopted_and_clears_ours() {
        val after = SyncState(clientIgnFly = 7).withIgnoringOnTheFly(IgnoringOnTheFlyData(server = 3))
        assertEquals(3, after.serverIgnFly)
        assertEquals(0, after.clientIgnFly)
    }

    @Test
    fun a_matching_client_counter_clears_ours_and_a_mismatched_one_does_not() {
        assertEquals(0, SyncState(clientIgnFly = 4).withIgnoringOnTheFly(IgnoringOnTheFlyData(client = 4)).clientIgnFly)
        assertEquals(4, SyncState(clientIgnFly = 4).withIgnoringOnTheFly(IgnoringOnTheFlyData(client = 9)).clientIgnFly)
    }

    @Test
    fun while_we_are_ignoring_the_server_nothing_is_decided() {
        val state = anchored().copy(clientIgnFly = 2)
        val out = decideSync(playstate(position = 500.0), state, ctx())
        assertEquals(state, out.state, "the anchor must not move")
        assertTrue(out.actions.isEmpty())
    }

    // ---- aged position ----

    @Test
    fun a_playing_room_ages_the_position_by_the_forward_delay() {
        val out = decideSync(playstate(position = 100.0, paused = false), anchored(), ctx(age = 0.5))
        assertEquals(100.5 * 1000.0, out.state.globalPositionMs)
    }

    @Test
    fun a_paused_room_does_not_age_at_all() {
        val out = decideSync(playstate(position = 100.0, paused = true), anchored(paused = true), ctx(age = 0.5))
        assertEquals(100.0 * 1000.0, out.state.globalPositionMs)
    }

    // ---- first sync ----

    @Test
    fun the_first_state_with_media_hard_seeks_and_applies_the_room_pause() {
        val out = decideSync(playstate(position = 42.0, paused = true), SyncState(), ctx(hasMedia = true))
        val first = out.actions.filterIsInstance<SyncAction.FirstSync>().single()
        assertEquals(42_000L, first.seekToMs)
        assertTrue(first.paused)
    }

    @Test
    fun the_first_state_without_media_waits() {
        val out = decideSync(playstate(position = 42.0), SyncState(), ctx(hasMedia = false))
        assertTrue(out.actions.none { it is SyncAction.FirstSync })
        assertEquals(t0, out.state.lastGlobalUpdate, "the anchor is still taken")
    }

    @Test
    fun the_second_state_does_not_seek_again() {
        val out = decideSync(playstate(position = 42.0), anchored(), ctx())
        assertTrue(out.actions.none { it is SyncAction.FirstSync })
    }

    // ---- seek ----

    @Test
    fun a_seek_is_announced_and_gives_the_speed_back() {
        val out = decideSync(
            playstate(position = 300.0, doSeek = true, setBy = "peer"),
            anchored(speedChanged = true),
            ctx(playerSeconds = 300.0),
        )
        assertTrue(out.actions.indexOf(SyncAction.RestoreSpeed) <
            out.actions.indexOfFirst { it is SyncAction.SomeoneSeeked }, "speed is restored first")
        assertEquals("peer", out.actions.filterIsInstance<SyncAction.SomeoneSeeked>().single().by)
        assertTrue(!out.state.speedChanged)
    }

    @Test
    fun a_seek_with_no_author_is_not_announced() {
        val out = decideSync(playstate(doSeek = true, setBy = null), anchored(), ctx())
        assertTrue(out.actions.none { it is SyncAction.SomeoneSeeked })
    }

    @Test
    fun a_long_username_is_cut_to_the_protocol_limit() {
        val out = decideSync(
            playstate(position = 10.0, doSeek = true, setBy = "a".repeat(40)),
            anchored(), ctx(playerSeconds = 10.0),
        )
        assertEquals(16, out.actions.filterIsInstance<SyncAction.SomeoneSeeked>().single().by.length)
    }

    // ---- rewind ----

    @Test
    fun being_far_ahead_asks_the_room_to_come_back() {
        val out = decideSync(playstate(position = 100.0), anchored(), ctx(playerSeconds = 105.0))
        assertEquals(100.0, out.actions.filterIsInstance<SyncAction.SomeoneBehind>().single().toSeconds)
    }

    @Test
    fun being_ahead_but_inside_the_threshold_does_nothing() {
        val out = decideSync(playstate(position = 100.0), anchored(), ctx(playerSeconds = 103.9))
        assertTrue(out.actions.none { it is SyncAction.SomeoneBehind })
    }

    @Test
    fun a_seek_suppresses_the_rewind() {
        val out = decideSync(playstate(position = 100.0, doSeek = true), anchored(), ctx(playerSeconds = 110.0))
        assertTrue(out.actions.none { it is SyncAction.SomeoneBehind })
    }

    @Test
    fun rewind_switched_off_does_nothing() {
        val prefs = SyncPrefs(rewind = false, fastForward = true, slowdown = true, dontSlowWithMe = false)
        val out = decideSync(playstate(position = 100.0), anchored(), ctx(playerSeconds = 110.0, prefs = prefs))
        assertTrue(out.actions.none { it is SyncAction.SomeoneBehind })
    }

    @Test
    fun no_media_means_no_correction_at_all() {
        val out = decideSync(playstate(position = 100.0), anchored(), ctx(playerSeconds = 0.0, hasMedia = false))
        assertTrue(out.actions.none { it is SyncAction.SomeoneBehind })
    }

    @Test
    fun a_backgrounded_client_is_not_corrected() {
        val out = decideSync(playstate(position = 100.0), anchored(), ctx(playerSeconds = 110.0, background = true))
        assertTrue(out.actions.none { it is SyncAction.SomeoneBehind })
    }

    // ---- fastforward ----

    @Test
    fun a_normal_room_never_force_fastforwards() {
        val out = decideSync(playstate(position = 100.0), anchored(), ctx(playerSeconds = 90.0, follower = false))
        assertTrue(out.actions.none { it is SyncAction.SomeoneFastForwarded })
        assertEquals(null, out.state.behindFirstDetected, "and it does not start the clock either")
    }

    @Test
    fun a_follower_in_a_controlled_room_starts_the_clock_then_catches_up() {
        val first = decideSync(
            playstate(position = 100.0), anchored(),
            ctx(playerSeconds = 90.0, follower = true),
        )
        assertTrue(first.actions.none { it is SyncAction.SomeoneFastForwarded }, "not on the first sighting")
        assertEquals(t0, first.state.behindFirstDetected)

        val later = t0 + 4.seconds
        val second = decideSync(
            playstate(position = 104.0), first.state,
            ctx(playerSeconds = 90.0, follower = true, now = later),
        )
        val ff = second.actions.filterIsInstance<SyncAction.SomeoneFastForwarded>().single()
        assertEquals(104.0 + FASTFORWARD_EXTRA_TIME, ff.toSeconds)
        assertEquals(later + FASTFORWARD_RESET_THRESHOLD.seconds, second.state.behindFirstDetected, "cooldown armed")
    }

    @Test
    fun catching_back_up_clears_the_behind_clock() {
        val out = decideSync(
            playstate(position = 100.0),
            anchored(behindSince = t0 - 9.seconds),
            ctx(playerSeconds = 100.0, follower = true),
        )
        assertNull(out.state.behindFirstDetected)
    }

    @Test
    fun dont_slow_with_me_opts_into_fastforward_in_a_normal_room() {
        val prefs = SyncPrefs(rewind = true, fastForward = true, slowdown = true, dontSlowWithMe = true)
        val out = decideSync(
            playstate(position = 100.0), anchored(),
            ctx(playerSeconds = 90.0, follower = false, prefs = prefs),
        )
        assertEquals(t0, out.state.behindFirstDetected)
    }

    // ---- slowdown ----

    @Test
    fun drifting_ahead_of_someone_else_slows_us_down() {
        val out = decideSync(playstate(position = 100.0, setBy = "peer"), anchored(), ctx(playerSeconds = 102.0))
        assertEquals("peer", out.actions.filterIsInstance<SyncAction.SlowDown>().single().by)
        assertTrue(out.state.speedChanged)
    }

    @Test
    fun we_never_slow_down_for_our_own_state() {
        val out = decideSync(playstate(position = 100.0, setBy = "me"), anchored(), ctx(playerSeconds = 102.0, self = "me"))
        assertTrue(out.actions.none { it is SyncAction.SlowDown })
    }

    @Test
    fun closing_the_gap_gives_the_speed_back() {
        val out = decideSync(playstate(position = 100.0), anchored(speedChanged = true), ctx(playerSeconds = 100.05))
        assertTrue(SyncAction.RestoreSpeed in out.actions)
        assertTrue(!out.state.speedChanged)
    }

    @Test
    fun an_engine_that_cannot_change_speed_gets_its_speed_put_back() {
        val out = decideSync(playstate(position = 100.0), anchored(speedChanged = true), ctx(playerSeconds = 102.0, speed = false))
        assertTrue(SyncAction.RestoreSpeed in out.actions)
    }

    @Test
    fun a_paused_room_is_never_slowed() {
        val out = decideSync(playstate(position = 100.0, paused = true), anchored(paused = true), ctx(playerSeconds = 102.0))
        assertTrue(out.actions.none { it is SyncAction.SlowDown })
    }

    // ---- pause transitions ----

    @Test
    fun a_pause_transition_is_announced_once() {
        val out = decideSync(playstate(position = 10.0, paused = true, setBy = "peer"), anchored(paused = false), ctx(playerSeconds = 10.0))
        assertEquals("peer", out.actions.filterIsInstance<SyncAction.SomeonePaused>().single().by)
    }

    @Test
    fun the_same_pause_state_again_is_silent() {
        val out = decideSync(playstate(position = 10.0, paused = true, setBy = "peer"), anchored(paused = true), ctx(playerSeconds = 10.0))
        assertTrue(out.actions.none { it is SyncAction.SomeonePaused })
    }

    @Test
    fun an_unpause_transition_is_announced() {
        val out = decideSync(playstate(position = 10.0, paused = false, setBy = "peer"), anchored(paused = true), ctx(playerSeconds = 10.0))
        assertEquals("peer", out.actions.filterIsInstance<SyncAction.SomeonePlayed>().single().by)
    }

    @Test
    fun pausing_also_gives_the_speed_back_before_it_announces() {
        val out = decideSync(
            playstate(position = 10.0, paused = true, setBy = "peer"),
            anchored(paused = false, speedChanged = true),
            ctx(playerSeconds = 10.0),
        )
        assertTrue(out.actions.indexOf(SyncAction.RestoreSpeed) <
            out.actions.indexOfFirst { it is SyncAction.SomeonePaused })
        assertTrue(!out.state.speedChanged)
    }

    // ---- degenerate input ----

    @Test
    fun a_state_with_no_playstate_changes_nothing() {
        val state = anchored()
        val out = decideSync(null, state, ctx())
        assertEquals(state, out.state)
        assertTrue(out.actions.isEmpty())
    }

    @Test
    fun a_playstate_with_no_paused_field_changes_nothing() {
        val state = anchored()
        val out = decideSync(PlaystateData(position = 5.0, paused = null), state, ctx())
        assertEquals(state, out.state)
        assertTrue(out.actions.isEmpty())
    }

    @Test
    fun a_playstate_with_no_position_reads_as_zero_like_the_reference_client() {
        val out = decideSync(PlaystateData(position = null, paused = false, setBy = "peer"), anchored(), ctx())
        assertEquals(0.0, out.state.globalPositionMs)
    }
}
