package app.protocol.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * What we tell the room our position is. Getting this wrong does not desync us, it desyncs
 * everyone: the official server adopts its slowest watcher, so one honest-but-wrong zero from a
 * file that is still opening drags the whole room back to the start.
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

    // ---- what we advertise ----

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
 * A per-user offset for two rips of the same film that differ by an intro or a logo card. It
 * must shift what we do locally and nothing the room sees, or one person's offset would drag
 * everybody.
 */
class UserOffsetTest {

    private val t0 = kotlin.time.Instant.fromEpochMilliseconds(1_700_000_000_000L)

    private fun inputs(localMs: Double, offset: Double, deadline: kotlin.time.Instant? = null) =
        PositionInputs(
            now = t0,
            globalPositionMs = 100_000.0,
            globalPositionSetAt = t0,
            globalPaused = false,
            hasMedia = true,
            isInBackground = false,
            localPositionMs = localMs,
            durationMs = 7_200_000.0,
            awaitingRoomResyncDeadline = deadline,
            userOffsetSeconds = offset,
        )

    @Test
    fun what_we_advertise_is_our_position_less_our_own_offset() {
        // Our copy runs 12s ahead, so at 112s we are showing the room's 100s.
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
        // Our copy runs 12s ahead. At 112s we are exactly on the room's 100s, so a load that
        // has caught up must stop masking even though the raw numbers differ by twelve seconds.
        val r = reportablePosition(inputs(localMs = 112_000.0, offset = 12.0, deadline = t0 + 20.seconds))
        assertFalse(r.keepMasking, "an offset must not look like a permanent desync")
        assertEquals(100.0, r.positionSeconds, 1e-9)
    }

    @Test
    fun the_room_still_looks_desynced_when_it_genuinely_is() {
        val r = reportablePosition(inputs(localMs = 5_000.0, offset = 12.0, deadline = t0 + 20.seconds))
        assertTrue(r.keepMasking)
    }
}
