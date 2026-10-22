package app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.room.ui.bottombar.onBrandBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A label on a filled control must be readable against that fill. The add-media block is the
 * trap: it turns the accent into dark ink so that an accent fill reads on the brand gradient. A
 * confirm key that takes its label from a stored dark colour then draws dark on its own dark fill.
 */
class PaletteInkTest {

    private val violet = Color(0xFF9879EF)

    private val dark = Palette(
        ground = Color(0xFF121212),
        panel = Color(0xFF1B1B21),
        ink = Color.White,
        inkDim = Color.White.copy(alpha = 0.62f),
        inkFaint = Color.White.copy(alpha = 0.42f),
        rule = Color.White.copy(alpha = 0.10f),
        trackOff = Color.White.copy(alpha = 0.12f),
        accent = violet,
        brandField = listOf(violet, Color(0xFFC331D8), Color(0xFFD86B75)),
        ok = Palette.Ok,
        okText = Palette.Ok,
        warn = Color(0xFFD86B75),
        bad = Palette.Bad,
        disabled = Color.White.copy(alpha = 0.38f),
        isDark = true,
    )

    /** The WCAG contrast ratio, so that a threshold has a standard meaning. */
    private fun contrast(a: Color, b: Color): Float {
        val high = maxOf(a.luminance(), b.luminance())
        val low = minOf(a.luminance(), b.luminance())
        return (high + 0.05f) / (low + 0.05f)
    }

    @Test
    fun `a light fill takes dark ink and a dark fill takes light ink`() {
        assertEquals(Palette.VideoGround, dark.inkOn(violet))
        assertEquals(Palette.VideoGround, dark.inkOn(Color(0xFFFFD66F)))
        assertEquals(Color.White, dark.inkOn(Color(0xFF0E0E12)))
        assertEquals(Color.White, dark.inkOn(Color(0xFF2B1B4A)))
    }

    @Test
    fun `the confirm key on the brand block is not its own colour`() {
        val block = dark.overVideo().onBrandBlock()
        // On the brand block the accent is the ground colour, so a ground-coloured label vanishes.
        assertTrue(contrast(block.accent, block.ground) < 1.1f)
        assertTrue(
            contrast(block.accent, block.inkOn(block.accent)) > 4.5f,
            "an accent fill on the brand block is unreadable: fill ${block.accent}",
        )
    }

    @Test
    fun `every built in accent keeps a readable label on its fill`() {
        val accents = listOf(
            violet,
            Color(0xFFFF5100),
            Color(0xFFCFCFCF),
            Color(0xFFFFD66F),
            Color(0xFFB3B3B3),
            Color(0xFF1A1A2E),
        )
        for (accent in accents) {
            val p = dark.copy(accent = accent)
            assertTrue(contrast(accent, p.inkOn(accent)) > 4.5f, "unreadable label on $accent")
        }
    }
}
