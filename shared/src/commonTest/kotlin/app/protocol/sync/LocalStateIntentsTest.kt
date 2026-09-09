package app.protocol.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalStateIntentsTest {
    private fun seek(from: Long, to: Long, playing: Boolean = false, undo: Boolean = true) =
        LocalStateIntent(to / 1000.0, playing, LocalSeek(from, to, undo))

    @Test
    fun rapid_seeks_keep_only_the_latest_unsent_intent() {
        val intents = LocalStateIntents()
        intents.offer(seek(10_000, 30_000))
        val first = intents.takeReady(true)!!
        intents.sent(first, 1, 0)
        intents.offer(seek(30_000, 60_000))
        assertNull(intents.takeReady(false))
        intents.offer(seek(60_000, 90_000))
        assertNull(intents.takeReady(false))
        assertEquals(LocalSeek(10_000, 30_000), intents.consumeSeekEcho(30.0, 1, 500))
        assertEquals(seek(60_000, 90_000), intents.takeReady(true))
        assertNull(intents.takeReady(true))
    }

    @Test
    fun pause_after_a_queued_seek_preserves_the_seek_and_latest_pause_state() {
        val intents = LocalStateIntents()
        intents.offer(seek(30_000, 60_000, playing = true))
        intents.offer(LocalStateIntent(60.2, playing = false))
        assertEquals(seek(30_000, 60_000), intents.takeReady(true))
    }

    @Test
    fun play_pause_play_coalesces_without_inventing_a_seek() {
        val intents = LocalStateIntents()
        intents.offer(LocalStateIntent(10.0, playing = true))
        intents.offer(LocalStateIntent(10.1, playing = false))
        intents.offer(LocalStateIntent(10.1, playing = true))
        assertEquals(LocalStateIntent(10.1, playing = true), intents.takeReady(true))
    }

    @Test
    fun seek_after_pause_replaces_position_and_preserves_its_own_origin() {
        val intents = LocalStateIntents()
        intents.offer(LocalStateIntent(10.0, playing = false))
        intents.offer(seek(10_000, 80_000))
        assertEquals(seek(10_000, 80_000), intents.takeReady(true))
    }

    @Test
    fun duplicate_echo_cannot_consume_the_next_different_seek_origin() {
        val intents = LocalStateIntents()
        intents.sent(seek(10_000, 30_000), 1, 0)
        assertEquals(LocalSeek(10_000, 30_000), intents.consumeSeekEcho(30.0, 1, 100))
        assertNull(intents.consumeSeekEcho(30.0, 1, 100))
        intents.sent(seek(60_000, 90_000), 1, 100)
        assertNull(intents.consumeSeekEcho(30.0, 1, 200))
        assertEquals(LocalSeek(60_000, 90_000), intents.consumeSeekEcho(90.0, 1, 200))
    }

    @Test
    fun mismatched_counter_does_not_spend_the_origin() {
        val intents = LocalStateIntents()
        intents.sent(seek(10_000, 30_000), 3, 0)
        assertNull(intents.consumeSeekEcho(30.0, 2, 500))
        assertEquals(LocalSeek(10_000, 30_000), intents.consumeSeekEcho(30.0, 3, 500))
    }

    @Test
    fun aged_playing_echo_matches_but_an_unrelated_target_does_not() {
        val intents = LocalStateIntents()
        intents.sent(seek(10_000, 30_000, playing = true), 1, 1_000)
        assertNull(intents.consumeSeekEcho(50.0, 1, 2_000))
        assertEquals(LocalSeek(10_000, 30_000), intents.consumeSeekEcho(30.25, 1, 2_000))
    }

    @Test
    fun undo_flag_survives_queue_and_echo_without_creating_redo_history() {
        val intents = LocalStateIntents()
        intents.offer(seek(30_000, 10_000, undo = false))
        val sent = intents.takeReady(true)!!
        intents.sent(sent, 1, 0)
        assertEquals(LocalSeek(30_000, 10_000, false), intents.consumeSeekEcho(10.0, 1, 100))
    }

    @Test
    fun resetting_media_room_or_connection_drops_pending_and_echo_metadata() {
        val intents = LocalStateIntents()
        intents.sent(seek(10_000, 30_000), 1, 0)
        intents.offer(seek(30_000, 60_000))
        intents.clear()
        assertNull(intents.takeReady(true))
        assertNull(intents.consumeSeekEcho(30.0, 1, 100))
    }

    @Test
    fun waiting_and_repeated_ack_checks_never_resend_an_intent() {
        val intents = LocalStateIntents()
        intents.offer(seek(10_000, 30_000))
        repeat(10_000) { assertNull(intents.takeReady(false)) }
        assertEquals(seek(10_000, 30_000), intents.takeReady(true))
        repeat(10_000) { assertNull(intents.takeReady(true)) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun older_queued_room_seek_and_pause_cannot_override_a_new_local_seek() = runTest {
        val intents = LocalStateIntents()
        val decidedRevision = intents.revision
        var position = 10_000L
        var paused = false
        // The inbound consumer has decided these actions; Main has not run them yet.
        launch {
            if (intents.isCurrent(decidedRevision)) { position = 30_000L; paused = true }
        }
        intents.offer(seek(10_000, 90_000, playing = true))
        position = 90_000L
        runCurrent()
        assertEquals(90_000L, position)
        assertFalse(paused)
    }

    @Test
    fun reset_invalidates_queued_transport_even_when_media_object_is_unchanged() {
        val intents = LocalStateIntents()
        val old = intents.revision
        intents.clear()
        assertFalse(intents.isCurrent(old))
    }

    @Test
    fun sending_and_acknowledging_do_not_invent_a_new_user_command_revision() {
        val intents = LocalStateIntents()
        intents.offer(seek(10_000, 30_000))
        val revision = intents.revision
        val intent = intents.takeReady(true)!!
        intents.sent(intent, 1, 0)
        intents.consumeSeekEcho(30.0, 1, 100)
        assertTrue(intents.isCurrent(revision))
    }
}
