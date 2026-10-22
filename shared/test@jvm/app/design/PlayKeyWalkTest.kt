package app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import app.room.ui.misc.walkingBrand
import app.theme.Space
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

/**
 * The play key's gradient moves while the engine (the video player) buffers, and it stops on a
 * whole phase. The stop looks smooth only if a whole phase draws the same picture as idle. A seam
 * there shows as a jump at the end of every buffering period.
 */
class PlayKeyWalkTest {
    private val colors = listOf(Color(0xFF9879EF), Color(0xFFC331D8), Color(0xFFD86B75))

    private fun frame(phase: Float): ByteArray {
        val name = "play-key-walk-${phase.toString().replace('.', '_')}"
        return DesignHarness.render(name, widthDp = 80, heightDp = 80) {
            Box(Modifier.size(Space.hero).drawBehind { drawRect(walkingBrand(colors, phase, size.width)) })
        }.file.readBytes()
    }

    @Test
    fun aWholePhaseDrawsTheIdleGradient() {
        assertContentEquals(frame(0f), frame(1f))
        assertContentEquals(frame(0f), frame(2f))
    }

    @Test
    fun aHalfPhaseIsVisiblyElsewhere() {
        assertFalse(frame(0f).contentEquals(frame(0.5f)))
    }
}
