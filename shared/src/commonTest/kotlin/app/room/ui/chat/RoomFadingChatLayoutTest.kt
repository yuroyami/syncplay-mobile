package app.room.ui.chat

import app.room.models.Message
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RoomFadingChatLayoutTest {

    @Test
    fun ineligibleMessageFadesTheExistingSnapshotWithoutReplacingIt() {
        val incoming = Message(sender = "friend", content = "hello")
        val visible = nextFadingMessagePresentation(FadingMessagePresentation(), incoming)

        val selfMessage = Message(sender = "me", content = "reply", isMainUser = true)
        val fading = nextFadingMessagePresentation(visible, selfMessage)

        assertSame(incoming, fading.message)
        assertFalse(fading.visible)
    }

    @Test
    fun unseenIncomingMessageReplacesTheSnapshotAndRestartsVisibility() {
        val first = Message(sender = "one", content = "first")
        val second = Message(sender = "two", content = "second")
        val current = FadingMessagePresentation(message = first, visible = false)

        val next = nextFadingMessagePresentation(current, second)

        assertSame(second, next.message)
        assertTrue(next.visible)
    }
}
