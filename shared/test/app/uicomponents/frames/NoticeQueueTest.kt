package app.uicomponents.frames

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A notice leaves by fading: the queue marks it, and only the host removes it. */
class NoticeQueueTest {
    @Test
    fun aDismissedNoticeStaysUntilItsFadeEnds() {
        val queue = NoticeQueue()
        queue.post("one", holdMs = 1000)
        val item = queue.items.single()
        queue.dismiss(item)
        assertTrue(item.leaving)
        assertEquals(1, queue.items.size, "still on screen while it fades")
        queue.remove(item)
        assertTrue(queue.items.isEmpty())
    }

    @Test
    fun aFullQueueFadesTheOldestInsteadOfCuttingIt() {
        val queue = NoticeQueue(max = 2)
        queue.post("one", holdMs = 1000)
        queue.post("two", holdMs = 1000)
        queue.post("three", holdMs = 1000)
        assertEquals(listOf("one", "two", "three"), queue.items.map { it.text })
        assertEquals(listOf(true, false, false), queue.items.map { it.leaving })
    }

    @Test
    fun aWarningIsNotPushedOutByAnInfo() {
        val queue = NoticeQueue(max = 1)
        queue.post("careful", NoticeSeverity.Warn, holdMs = 1000)
        queue.post("hello", holdMs = 1000)
        assertEquals(listOf("careful"), queue.items.map { it.text })
        assertFalse(queue.items.single().leaving)
    }

    @Test
    fun clearFadesEveryNotice() {
        val queue = NoticeQueue()
        queue.post("one", holdMs = 1000)
        queue.post("two", holdMs = 1000)
        queue.clear()
        assertTrue(queue.items.all { it.leaving })
    }

    @Test
    fun withNoHostTheFadingNoticesDoNotPileUp() {
        val queue = NoticeQueue(max = 2)
        repeat(50) { queue.post("n$it", holdMs = 1000) }
        assertTrue(queue.items.size <= 6, "size ${queue.items.size}")
        assertEquals(2, queue.items.count { !it.leaving })
    }
}
