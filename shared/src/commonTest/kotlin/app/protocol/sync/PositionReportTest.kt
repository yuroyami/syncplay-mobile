package app.protocol.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The position that the client reports to the room (the group of people watching together). A
 * wrong report desyncs everyone, not only this client: the official server adopts its slowest
 * watcher, so one wrong zero from a file that is still opening drags the whole room to the start.
 */
class PositionReportTest {

    private val t0 = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    private fun inputs(
        now: Instant = t0,
        globalMs: Double = 100_000.0,
        setAt: Instant? = t0,
        paused: Boolean = false,
        hasMedia: Boolean = true,
        background: Boolean = false,
        localMs: Double = 100_000.0,
        durationMs: Double = 7_200_000.0,
        deadline: Instant? = null,
    ) = PositionInputs(
        now = now,
        globalPositionMs = globalMs,
        globalPositionSetAt = setAt,
        globalPaused = paused,
        hasMedia = hasMedia,
        isInBackground = background,
        localPositionMs = localMs,
        durationMs = durationMs,
        awaitingRoomResyncDeadline = deadline,
    )

    // ---- extrapolation ----

    @Test
    fun a_playing_room_carries_its_position_forward_by_the_clock() {
        val at = extrapolatedGlobalPositionMs(inputs(now = t0 + 3.seconds))
        assertEquals(103_000.0, at)
    }

    @Test
    fun a_paused_room_stays_where_it_was() {
        val at = extrapolatedGlobalPositionMs(inputs(now = t0 + 3.seconds, paused = true))
        assertEquals(100_000.0, at)
    }

    @Test
    fun with_no_anchor_there_is_nothing_to_carry_forward() {
        val at = extrapolatedGlobalPositionMs(inputs(now = t0 + 3.seconds, setAt = null))
        assertEquals(100_000.0, at)
    }

    // ---- the reported position ----

    @Test
    fun with_no_file_we_advertise_the_room_and_never_a_bare_zero() {
        val r = reportablePosition(inputs(hasMedia = false, localMs = 0.0))
        assertEquals(100.0, r.positionSeconds)
        assertTrue(r.keepMasking)
    }

    @Test
    fun backgrounded_we_advertise_the_room_so_nobody_adopts_a_frozen_watcher() {
        val r = reportablePosition(inputs(background = true, localMs = 12_000.0))
        assertEquals(100.0, r.positionSeconds)
        assertTrue(r.keepMasking)
    }

    @Test
    fun with_nothing_to_mask_we_tell_the_truth() {
        val r = reportablePosition(inputs(localMs = 42_000.0, deadline = null))
        assertEquals(42.0, r.positionSeconds)
        assertFalse(r.keepMasking)
    }

    @Test
    fun a_file_still_opening_advertises_the_room_not_its_own_zero() {
        val r = reportablePosition(inputs(localMs = 300.0, deadline = t0 + 20.seconds))
        assertEquals(100.0, r.positionSeconds, "advertising 0.3 would drag the room to the start")
        assertTrue(r.keepMasking)
    }

    @Test
    fun masking_stops_the_moment_the_engine_catches_up() {
        val r = reportablePosition(inputs(localMs = 99_500.0, deadline = t0 + 20.seconds))
        assertEquals(99.5, r.positionSeconds)
        assertFalse(r.keepMasking, "within a second of the room counts as caught up")
    }

    @Test
    fun masking_stops_when_the_file_is_too_short_to_ever_catch_up() {
        // A mismatched file: the room is at 100s, this file ends at 100.5s.
        val r = reportablePosition(inputs(localMs = 5_000.0, durationMs = 100_500.0, deadline = t0 + 20.seconds))
        assertEquals(5.0, r.positionSeconds)
        assertFalse(r.keepMasking)
    }

    @Test
    fun masking_stops_at_the_deadline_so_a_real_desync_stays_visible() {
        val r = reportablePosition(inputs(now = t0 + 30.seconds, localMs = 5_000.0, deadline = t0 + 20.seconds))
        assertEquals(5.0, r.positionSeconds, "a client stuck buffering must stop hiding it")
        assertFalse(r.keepMasking)
    }

    @Test
    fun an_unknown_duration_never_counts_as_too_short() {
        val r = reportablePosition(inputs(localMs = 300.0, durationMs = 0.0, deadline = t0 + 20.seconds))
        assertEquals(100.0, r.positionSeconds)
        assertTrue(r.keepMasking)
    }

    @Test
    fun the_room_position_we_mask_with_is_the_extrapolated_one_not_the_last_snapshot() {
        val r = reportablePosition(inputs(now = t0 + 4.seconds, localMs = 300.0, deadline = t0 + 20.seconds))
        assertEquals(104.0, r.positionSeconds, "masking with a stale snapshot would still drag the room")
    }
}

/**
 * A per-user offset lines up two copies of the same film that differ by an intro or a logo card.
 * The offset must shift only the local playback and nothing that the room sees. Otherwise one
 * person's offset would drag everybody.
 */
class UserOffsetTest {

    private val t0 = kotlin.time.Instant.fromEpochMilliseconds(1_700_000_000_000L)

    private fun inputs(
        localMs: Double,
        offset: Double,
        deadline: kotlin.time.Instant? = null,
        globalMs: Double = 100_000.0,
        durationMs: Double = 7_200_000.0,
    ) =
        PositionInputs(
            now = t0,
            globalPositionMs = globalMs,
            globalPositionSetAt = t0,
            globalPaused = false,
            hasMedia = true,
            isInBackground = false,
            localPositionMs = localMs,
            durationMs = durationMs,
            awaitingRoomResyncDeadline = deadline,
            userOffsetSeconds = offset,
        )

    @Test
    fun what_we_advertise_is_our_position_less_our_own_offset() {
        // The local copy runs 12 s ahead, so the local 112 s shows the room's 100 s.
        val r = reportablePosition(inputs(localMs = 112_000.0, offset = 12.0))
        assertEquals(100.0, r.positionSeconds, 1e-9)
    }

    @Test
    fun a_negative_offset_works_the_other_way() {
        val r = reportablePosition(inputs(localMs = 88_000.0, offset = -12.0))
        assertEquals(100.0, r.positionSeconds, 1e-9)
    }

    @Test
    fun no_offset_changes_nothing() {
        val r = reportablePosition(inputs(localMs = 42_000.0, offset = 0.0))
        assertEquals(42.0, r.positionSeconds, 1e-9)
    }

    @Test
    fun convergence_is_judged_in_the_rooms_frame_not_ours() {
        // The local copy runs 12 s ahead, so the local 112 s is exactly the room's 100 s. A load
        // that has caught up must stop masking, even though the raw numbers differ by 12 s.
        val r = reportablePosition(inputs(localMs = 112_000.0, offset = 12.0, deadline = t0 + 20.seconds))
        assertFalse(r.keepMasking, "an offset must not look like a permanent desync")
        assertEquals(100.0, r.positionSeconds, 1e-9)
    }

    @Test
    fun the_room_still_looks_desynced_when_it_genuinely_is() {
        val r = reportablePosition(inputs(localMs = 5_000.0, offset = 12.0, deadline = t0 + 20.seconds))
        assertTrue(r.keepMasking)
    }

    @Test
    fun an_impossible_local_target_is_impossible_in_local_time() {
        // The local copy is 100 s long and runs 10 s ahead. The room is at 95 s, which is 105 s in
        // the local copy: past the end. A check of 95 against 100 would keep masking forever.
        val far = t0 + 20.seconds
        val r = reportablePosition(
            inputs(localMs = 0.0, offset = 10.0, deadline = far, globalMs = 95_000.0, durationMs = 100_000.0)
        )
        assertFalse(r.keepMasking, "a target past the end of our copy is not reachable")
    }

    @Test
    fun a_negative_offset_can_make_the_same_target_reachable() {
        // Same room position, but the local copy runs 10 s behind: the room's 95 s is the local
        // 85 s. That is inside a 100 s file, so masking stays on until playback gets there.
        val far = t0 + 20.seconds
        val r = reportablePosition(
            inputs(localMs = 0.0, offset = -10.0, deadline = far, globalMs = 95_000.0, durationMs = 100_000.0)
        )
        assertTrue(r.keepMasking)
    }

    @Test
    fun a_target_before_the_start_of_our_copy_is_impossible() {
        // The room is at 5 s and the local copy runs 10 s behind, so the local target is minus 5 s.
        val far = t0 + 20.seconds
        val r = reportablePosition(
            inputs(localMs = 50_000.0, offset = -10.0, deadline = far, globalMs = 5_000.0, durationMs = 100_000.0)
        )
        assertFalse(r.keepMasking)
    }
}
