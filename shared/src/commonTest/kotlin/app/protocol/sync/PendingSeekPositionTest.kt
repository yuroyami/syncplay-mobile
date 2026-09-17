package app.protocol.sync

import app.protocol.wire.PlaystateData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PendingSeekPositionTest {
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    private fun inputs(localMs: Long, pending: PendingSeekPositions) = PositionInputs(
        now = now,
        globalPositionMs = 600_000.0,
        globalPositionSetAt = now,
        globalPaused = false,
        hasMedia = true,
        isInBackground = false,
        localPositionMs = localMs.toDouble(),
        durationMs = 7_200_000.0,
        awaitingRoomResyncDeadline = null,
        pendingSeekPositionMs = pending.current?.targetMs,
    )

    @Test
    fun a_seek_ack_and_subsequent_reports_cannot_advertise_the_pre_seek_cache() = runTest {
        val pending = PendingSeekPositions()
        var playerPosition = 100_000L
        val seek = assertNotNull(pending.begin(SyncAction.SomeoneSeeked("A", 600.125), "B"))
        val job = launch { playerPosition = seek.targetMs }
        job.invokeOnCompletion { pending.complete(seek) }

        repeat(3) {
            assertEquals(600.125, reportablePosition(inputs(playerPosition, pending)).positionSeconds)
        }
        assertEquals(100_000L, playerPosition, "Main has not submitted the seek yet")
        runCurrent()
        assertNull(pending.current)
        assertEquals(600.125, reportablePosition(inputs(playerPosition, pending)).positionSeconds)
    }

    @Test
    fun a_tracker_sample_before_the_player_command_does_not_replace_the_target() {
        val pending = PendingSeekPositions()
        pending.begin(SyncAction.SomeoneSeeked("A", 600.0), "B")
        assertEquals(600.0, reportablePosition(inputs(100_250L, pending)).positionSeconds)
    }

    @Test
    fun an_older_completion_cannot_release_a_newer_seek_even_to_the_same_target() {
        val pending = PendingSeekPositions()
        val old = assertNotNull(pending.begin(SyncAction.SomeoneSeeked("A", 600.0), "B"))
        val latest = assertNotNull(pending.begin(SyncAction.SomeoneSeeked("A", 600.0), "B"))
        pending.complete(old)
        assertSame(latest, pending.current)
        pending.complete(latest)
        assertNull(pending.current)
    }

    @Test
    fun cancelling_before_main_runs_releases_the_target() = runTest {
        val pending = PendingSeekPositions()
        val seek = assertNotNull(pending.begin(SyncAction.SomeoneSeeked("A", 600.0), "B"))
        val job = launch { error("A cancelled seek must not reach the engine") }
        job.invokeOnCompletion { pending.complete(seek) }
        job.cancel()
        runCurrent()
        assertNull(pending.current)
        assertEquals(100.0, reportablePosition(inputs(100_000L, pending)).positionSeconds)
    }

    @Test
    fun clearing_for_a_new_local_command_or_media_does_not_leave_a_mask() {
        val pending = PendingSeekPositions()
        val old = assertNotNull(pending.begin(SyncAction.SomeoneSeeked("A", 600.0), "B"))
        pending.clear()
        assertNull(pending.current)
        val latest = assertNotNull(pending.begin(SyncAction.FirstSync(300_000L, true), "B"))
        pending.complete(old)
        assertSame(latest, pending.current)
    }

    @Test
    fun self_echoes_and_non_seek_actions_do_not_install_or_replace_a_target() {
        val pending = PendingSeekPositions()
        val seek = pending.begin(SyncAction.SomeoneSeeked("A", 600.0), "B")
        for (action in listOf(
            SyncAction.SomeoneSeeked("B", 50.0),
            SyncAction.SomeoneBehind("B", 50.0),
            SyncAction.SomeoneFastForwarded("B", 50.0),
            SyncAction.SomeonePaused("A"),
            SyncAction.SomeonePlayed("A"),
            SyncAction.RestoreSpeed,
        )) {
            assertNull(pending.begin(action, "B"))
            assertSame(seek, pending.current)
        }
    }

    @Test
    fun backward_and_first_sync_targets_keep_offsets_and_subsecond_precision() {
        val pending = PendingSeekPositions()
        pending.begin(SyncAction.SomeoneSeeked("A", 42.625), "B")
        val report = reportablePosition(inputs(600_000L, pending).copy(userOffsetSeconds = 12.5))
        assertEquals(30.125, report.positionSeconds)
        pending.begin(SyncAction.FirstSync(0L, true), "B")
        assertEquals(0.0, reportablePosition(inputs(600_000L, pending)).positionSeconds)
    }

    @Test
    fun a_queued_seek_does_not_disarm_the_existing_load_mask() {
        val pending = PendingSeekPositions()
        val seek = assertNotNull(pending.begin(SyncAction.FirstSync(600_000L, false), "B"))
        val loading = inputs(0L, pending).copy(awaitingRoomResyncDeadline = now + 20.seconds)
        assertTrue(reportablePosition(loading).keepMasking)
        pending.complete(seek)
        assertTrue(reportablePosition(loading.copy(pendingSeekPositionMs = null)).keepMasking)
    }

    @Test
    fun queued_backward_seeks_are_not_mistaken_for_drift_on_the_next_state() {
        val state = SyncState(globalPaused = false, lastGlobalUpdate = now)
        val context = SyncContext(
            now = now, playerPositionMs = 600_000.0, hasMedia = true, isInBackground = false,
            supportsSpeedAdjustment = true, selfName = "B", followerInControlledRoom = false,
            prefs = SyncPrefs(true, true, true, true), messageAge = 0.0, seekPending = true,
        )
        val tick = PlaystateData(position = 100.0, paused = false, doSeek = false, setBy = "A")
        assertTrue(decideSync(tick, state, context).actions.isEmpty())
        val settled = decideSync(tick, state, context.copy(seekPending = false))
        assertTrue(settled.actions.any { it is SyncAction.SomeoneBehind })

        val newerSeek = decideSync(tick.copy(doSeek = true, position = 300.0), state, context)
        assertTrue(newerSeek.actions.any { it is SyncAction.SomeoneSeeked && it.toSeconds == 300.0 })
        val pause = decideSync(tick.copy(paused = true), state, context)
        assertTrue(pause.actions.any { it is SyncAction.SomeonePaused })
        assertFalse(pause.actions.any { it is SyncAction.SomeoneBehind })
    }
}
