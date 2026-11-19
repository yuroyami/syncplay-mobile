package app.room

import app.protocol.sync.SyncPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The hold reasons, the rule that decides background sound, and the sync preferences away from the screen. */
class PlaybackHoldsTest {

    @Test
    fun only_the_first_reason_holds_and_only_the_last_one_releases() {
        val holds = PlaybackHolds()
        assertTrue(holds.add(HoldReason.AudioFocus), "the first reason makes the device held")
        assertFalse(holds.add(HoldReason.Route), "a second reason changes nothing")
        assertFalse(holds.add(HoldReason.Route), "the same reason twice changes nothing")
        assertFalse(holds.remove(HoldReason.AudioFocus), "one reason is left")
        assertFalse(holds.remove(HoldReason.Interruption), "a reason that was not set releases nothing")
        assertTrue(holds.held)
        assertTrue(holds.remove(HoldReason.Route), "the last reason releases the device")
        assertFalse(holds.held)
    }

    @Test
    fun clear_says_whether_anything_was_held() {
        val holds = PlaybackHolds()
        assertFalse(holds.clear())
        holds.add(HoldReason.LeftScreen)
        assertTrue(holds.clear())
        assertFalse(holds.has(HoldReason.LeftScreen))
    }

    @Test
    fun background_sound_needs_every_condition() {
        assertTrue(keepsSoundAway(settingOn = true, platformSupports = true, hasMedia = true, playerReady = true, connectedOrSolo = true))
        val off = listOf(
            keepsSoundAway(false, true, true, true, true),
            keepsSoundAway(true, false, true, true, true),
            keepsSoundAway(true, true, false, true, true),
            keepsSoundAway(true, true, true, false, true),
            keepsSoundAway(true, true, true, true, false),
        )
        assertTrue(off.none { it }, "any missing condition means the device holds instead")
    }

    @Test
    fun away_from_the_screen_the_device_follows_and_corrects_itself() {
        val user = SyncPrefs(rewind = false, fastForward = false, slowdown = false, dontSlowWithMe = false)
        assertEquals(SyncPrefs(rewind = true, fastForward = true, slowdown = true, dontSlowWithMe = true), user.whenAway())
    }
}
