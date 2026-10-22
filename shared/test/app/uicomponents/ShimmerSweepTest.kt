package app.uicomponents

import androidx.compose.ui.geometry.Offset
import app.uicomponents.controls.shimmerSweep
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The shimmer loops by restarting its phase. The restart is invisible only if no pixel of the tile
 * sits inside the light band at either end of the loop. The corners are the extreme points, so the
 * test checks them. A corner lit at phase 0 or phase 1 shows as a jump on every restart.
 */
class ShimmerSweepTest {

    /** Where [p] falls along the gradient: 0 at its start colour, 1 at its end colour, lit in between. */
    private fun along(p: Offset, start: Offset, end: Offset): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        return ((p.x - start.x) * dx + (p.y - start.y) * dy) / (dx * dx + dy * dy)
    }

    private fun corners(w: Float, h: Float) = listOf(Offset(0f, 0f), Offset(w, 0f), Offset(0f, h), Offset(w, h))

    @Test
    fun `no corner is lit at either end of the loop`() {
        for ((w, h) in listOf(100f to 100f, 200f to 100f, 100f to 300f, 37f to 41f)) {
            for (phase in listOf(0f, 1f)) {
                val (start, end) = shimmerSweep(phase, w, h)
                for (corner in corners(w, h)) {
                    val t = along(corner, start, end)
                    assertTrue(t <= 0.0001f || t >= 0.9999f, "corner $corner lit at phase $phase for ${w}x$h (t=$t)")
                }
            }
        }
    }

    @Test
    fun `the band crosses the middle of the tile halfway through`() {
        val (start, end) = shimmerSweep(0.5f, 120f, 80f)
        val t = along(Offset(60f, 40f), start, end)
        assertTrue(t > 0.45f && t < 0.55f, "centre should be lit at phase 0.5, t=$t")
    }

    @Test
    fun `the band enters from one side and leaves from the other`() {
        val (s0, _) = shimmerSweep(0f, 100f, 100f)
        val (s1, _) = shimmerSweep(1f, 100f, 100f)
        assertTrue(s0.x < 0f && s1.x > 100f)
    }
}
