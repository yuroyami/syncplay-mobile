package app.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.uicomponents.controls.waitingShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The brand system: the brand ramp of every built-in theme, the colours fitted next to it, the
 * eased video shades and the shape of a key that waits.
 */
class BrandSystemTest {

    /** A theme's palette on its own ground, and the palette that the room uses over the video. */
    private fun palettes(theme: SaveableTheme): List<Palette> {
        val own = Palette.from(theme.dynamicScheme, theme)
        return listOf(own, own.overVideo())
    }

    @Test
    fun `the ink on the brand ramp reads on every stop`() {
        for (theme in availableThemes) for (p in palettes(theme)) {
            assertEquals(RAMP_STOPS, p.brandField.size)
            val worst = p.brandField.minOf { contrastRatio(it, p.brandInk) }
            assertTrue(worst >= 4.5f, "${theme.name}: the ink on the ramp reads at $worst:1")
        }
    }

    @Test
    fun `the accent and the warning read on their ground`() {
        for (theme in availableThemes) for (p in palettes(theme)) {
            val accent = contrastRatio(p.accent, p.ground)
            val warn = contrastRatio(p.warn, p.ground)
            assertTrue(accent >= 4.5f, "${theme.name}: the accent reads at $accent:1")
            assertTrue(warn >= 4.5f, "${theme.name}: the warning reads at $warn:1")
        }
    }

    @Test
    fun `no theme sets the warning colour`() {
        for (theme in availableThemes) {
            val (own, video) = palettes(theme)
            assertEquals(if (theme.isDark) Palette.Warn else Palette.WarnOnLight, own.warn)
            assertEquals(Palette.Warn, video.warn)
        }
    }

    @Test
    fun `the middle of a colourful ramp keeps its colour`() {
        // Blended in sRGB, the middle of Neon's ramp turned brown, and Violet's lost colour.
        for (theme in listOf(TRINITY, PYNCSLAY)) {
            val weakest = theme.brandRamp.minOf { it.toLch().c }
            assertTrue(weakest >= 0.1f, "${theme.name}: the weakest stop has chroma $weakest")
        }
    }

    @Test
    fun `an eased shade fades from full strength to clear`() {
        val falling = easedShade(0.7f, darkAtStart = true)
        assertEquals(0f, falling.first().first)
        assertEquals(1f, falling.last().first)
        assertEquals(0.7f, falling.first().second.alpha, 0.002f)
        assertEquals(0f, falling.last().second.alpha, 0.002f)
        falling.toList().zipWithNext().forEach { (a, b) -> assertTrue(b.second.alpha <= a.second.alpha) }

        val rising = easedShade(0.6f, darkAtStart = false)
        assertEquals(0f, rising.first().second.alpha, 0.002f)
        assertEquals(0.6f, rising.last().second.alpha, 0.002f)
    }

    @Test
    fun `a waiting key rounds into a capsule`() {
        fun radius(waiting: Float): Float {
            val outline = waitingShape(waiting).createOutline(Size(200f, 48f), LayoutDirection.Ltr, Density(1f))
            return (outline as Outline.Rounded).roundRect.topLeftCornerRadius.x
        }
        assertEquals(Radius.control.value, radius(0f), 0.01f)
        assertEquals(24f, radius(1f), 0.01f)
    }
}
