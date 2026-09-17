package app.room

import app.room.models.Message
import app.room.models.fadingMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.seconds

class RoomTransientStateTest {
    @Test fun messagesExpireIndividuallyWithoutNewTraffic() {
        val clock = TestTimeSource()
        val first = Message(sender = "a", receivedAt = clock.markNow())
        clock += 2.seconds
        val second = Message(sender = "b", receivedAt = clock.markNow())
        clock += 1.seconds
        assertEquals(listOf(second), fadingMessages(listOf(first, second), 3.seconds, 3, emptySet()))
        clock += 2.seconds
        assertTrue(fadingMessages(listOf(first, second), 3.seconds, 3, emptySet()).isEmpty())
    }

    @Test fun cappedListsAndReenteringHiddenHudDoNotReviveOldMessages() {
        val clock = TestTimeSource()
        val old = Message(sender = "a", receivedAt = clock.markNow())
        clock += 10.seconds
        assertTrue(fadingMessages(listOf(old), 3.seconds, 3, emptySet()).isEmpty())
        val fresh = Message(sender = "b", receivedAt = clock.markNow())
        assertEquals(listOf(fresh), fadingMessages(listOf(fresh), 3.seconds, 3, emptySet()))
        assertTrue(fadingMessages(listOf(fresh), 3.seconds, 3, setOf("b")).isEmpty())
        fresh.seen = true
        assertTrue(fadingMessages(listOf(fresh), 3.seconds, 3, emptySet()).isEmpty())
    }

    @Test fun addMediaRestoresThePreviousPanelOnCompletionOrCancel() {
        val first = MutableStateFlow(true)
        val second = MutableStateFlow(false)
        val panels = SidePanelCoordinator(listOf(first, second))
        panels.expandMedia()
        assertTrue(panels.mediaExpanded.value)
        assertFalse(first.value)
        panels.collapseMedia()
        assertTrue(first.value)
        assertFalse(second.value)
        assertFalse(panels.mediaExpanded.value)
    }

    @Test fun choosingAnotherPanelCancelsTheBorrowedPanel() {
        val first = MutableStateFlow(true)
        val second = MutableStateFlow(false)
        val panels = SidePanelCoordinator(listOf(first, second))
        panels.expandMedia()
        panels.open(second, null)
        assertFalse(panels.mediaExpanded.value)
        panels.collapseMedia()
        assertFalse(first.value)
        assertTrue(second.value)
    }
}
