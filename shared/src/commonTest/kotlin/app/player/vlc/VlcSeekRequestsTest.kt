package app.player.vlc

import kotlin.test.Test
import kotlin.test.assertEquals

class VlcSeekRequestsTest {
    @Test
    fun known_duration_limits_the_target_including_remote_seeks() {
        assertEquals(180_000L, normalizeVlcSeekTarget(200_000L, 180_000L))
        assertEquals(180_000L, normalizeVlcSeekTarget(180_000L, 180_000L))
        assertEquals(0L, normalizeVlcSeekTarget(-50L, 180_000L))
    }

    @Test
    fun unknown_duration_preserves_long_media_without_overflowing_native_ticks() {
        val threeDaysMs = 3L * 24L * 60L * 60L * 1_000L
        assertEquals(threeDaysMs, normalizeVlcSeekTarget(threeDaysMs, 0L))
        assertEquals(threeDaysMs, normalizeVlcSeekTarget(threeDaysMs, -1L))
        assertEquals(Long.MAX_VALUE / 1_000L, normalizeVlcSeekTarget(Long.MAX_VALUE, 0L))
    }

    @Test
    fun startup_waits_without_submitting_and_only_the_latest_request_survives() {
        val requests = VlcSeekRequests(30_000L)
        requests.request(10_000L, 0L)
        assertEquals(VlcSeekDecision.Wait(10_000L), requests.poll(VlcSeekReadiness.OPENING, 0L, 100L))
        requests.request(20_000L, 200L)
        requests.request(30_000L, 300L)
        assertEquals(VlcSeekDecision.Wait(30_000L), requests.poll(VlcSeekReadiness.OPENING, 0L, 500L))
        assertEquals(VlcSeekDecision.Submit(30_000L), requests.poll(VlcSeekReadiness.READY, 180_000L, 600L))
        assertEquals(VlcSeekDecision.None, requests.poll(VlcSeekReadiness.READY, 180_000L, 700L))
    }

    @Test
    fun deferred_target_is_normalized_again_when_duration_becomes_known() {
        val requests = VlcSeekRequests(30_000L)
        requests.request(200_000L, 0L)
        assertEquals(VlcSeekDecision.Wait(200_000L), requests.poll(VlcSeekReadiness.OPENING, 0L, 100L))
        assertEquals(VlcSeekDecision.Submit(180_000L), requests.poll(VlcSeekReadiness.READY, 180_000L, 200L))
    }

    @Test
    fun unavailable_input_rejects_intent_instead_of_creating_a_position_guard() {
        val requests = VlcSeekRequests(30_000L)
        requests.request(30_000L, 0L)
        assertEquals(VlcSeekDecision.Rejected, requests.poll(VlcSeekReadiness.UNAVAILABLE, 180_000L, 100L))
        assertEquals(VlcSeekDecision.None, requests.poll(VlcSeekReadiness.READY, 180_000L, 200L))
    }

    @Test
    fun startup_wait_is_bounded_by_the_room_resync_window() {
        val requests = VlcSeekRequests(30_000L)
        requests.request(30_000L, 100L)
        assertEquals(VlcSeekDecision.Wait(30_000L), requests.poll(VlcSeekReadiness.OPENING, 0L, 30_099L))
        assertEquals(VlcSeekDecision.Rejected, requests.poll(VlcSeekReadiness.OPENING, 0L, 30_100L))
        assertEquals(VlcSeekDecision.None, requests.poll(VlcSeekReadiness.READY, 180_000L, 30_101L))
    }

    @Test
    fun changing_media_discards_a_deferred_command() {
        val requests = VlcSeekRequests(30_000L)
        requests.request(30_000L, 0L)
        requests.reset()
        assertEquals(VlcSeekDecision.None, requests.poll(VlcSeekReadiness.READY, 180_000L, 100L))
    }

    @Test
    fun replacement_waits_for_native_input_even_if_explicit_play_has_ended_priming() {
        val startup = VlcSeekStartup(30_000L)
        startup.begin(0L)
        // libVLC can still say Playing while replacement has removed its old input.
        assertEquals(VlcSeekReadiness.OPENING, startup.readiness(VlcSeekInputState.UNSEEKABLE, 0L, 0L, 100L))
        assertEquals(VlcSeekReadiness.OPENING, startup.readiness(VlcSeekInputState.OPENING, 0L, 0L, 200L))
        assertEquals(VlcSeekReadiness.READY, startup.readiness(VlcSeekInputState.SEEKABLE, 180_000L, 0L, 300L))
        assertEquals(VlcSeekReadiness.UNAVAILABLE, startup.readiness(VlcSeekInputState.INACTIVE, 0L, -1L, 400L))
    }

    @Test
    fun known_unseekable_native_input_closes_the_startup_window() {
        val startup = VlcSeekStartup(30_000L)
        startup.begin(0L)
        assertEquals(VlcSeekReadiness.UNAVAILABLE, startup.readiness(VlcSeekInputState.UNSEEKABLE, 180_000L, 0L, 100L))
        assertEquals(VlcSeekReadiness.UNAVAILABLE, startup.readiness(VlcSeekInputState.INACTIVE, 0L, 0L, 200L))
        startup.begin(300L)
        assertEquals(VlcSeekReadiness.UNAVAILABLE, startup.readiness(VlcSeekInputState.UNSEEKABLE, 0L, 50L, 400L))
    }

    @Test
    fun newer_requests_do_not_extend_the_media_startup_deadline() {
        val startup = VlcSeekStartup(30_000L)
        val requests = VlcSeekRequests(30_000L)
        startup.begin(0L)
        requests.request(30_000L, 29_999L)
        assertEquals(VlcSeekReadiness.OPENING, startup.readiness(VlcSeekInputState.INACTIVE, 0L, -1L, 29_999L))
        val expired = startup.readiness(VlcSeekInputState.OPENING, 0L, 0L, 30_000L)
        assertEquals(VlcSeekDecision.Rejected, requests.poll(expired, 0L, 30_000L))
    }

    @Test
    fun error_and_media_reset_close_startup_waiting() {
        val startup = VlcSeekStartup(30_000L)
        startup.begin(0L)
        assertEquals(VlcSeekReadiness.UNAVAILABLE, startup.readiness(VlcSeekInputState.FAILED, 0L, -1L, 100L))
        assertEquals(VlcSeekReadiness.UNAVAILABLE, startup.readiness(VlcSeekInputState.INACTIVE, 0L, -1L, 200L))
        startup.begin(300L)
        startup.reset()
        assertEquals(VlcSeekReadiness.UNAVAILABLE, startup.readiness(VlcSeekInputState.OPENING, 0L, 0L, 400L))
    }
}
