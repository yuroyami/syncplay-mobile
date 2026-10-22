package app.design

import app.uicomponents.frames.NoticeHost
import app.uicomponents.frames.NoticeQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A notice fades out before it leaves the stack, instead of vanishing from one frame to the next.
 * A frame here is 16 ms of animation time, and the fade takes 400 ms.
 */
class NoticeFadeTest {
    private fun fade(dismissAfterFrames: Int) {
        val queue = NoticeQueue()
        DesignHarness.drive(widthDp = 420, heightDp = 200, television = false, content = { NoticeHost(queue, overVideo = false) }) {
            DesignHarness.onUiThread { queue.post("Saved", holdMs = 60_000) }
            frames(dismissAfterFrames)
            val item = queue.items.single()
            DesignHarness.onUiThread { queue.dismiss(item) }
            frames(3)
            assertEquals(1, queue.items.size, "still on screen, fading")
            frames(40)
            assertTrue(queue.items.isEmpty(), "gone once the fade ends")
        }
    }

    @Test
    fun aShownNoticeFadesOutBeforeItLeaves() = fade(dismissAfterFrames = 40)

    @Test
    fun aNoticeDismissedWhileItFadesInFadesBackOut() = fade(dismissAfterFrames = 8)
}
