package app.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.sin

/**
 * Pictures that stand in for a video frame in a render, drawn in code so the tests need no image
 * file. [BrightScene] is the hard case for light keys and text: a sunny beach, brightest at the
 * bottom, where the keys sit. [NightScene] is the hard case for dark ones.
 */
object TestScenes {

    @Composable
    fun BrightScene(modifier: Modifier) = Canvas(modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.52f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF8CC8F5), Color(0xFFEAF6FF)), 0f, horizon), size = Size(w, horizon))
        drawCircle(Color(0xFFFFFBE8), radius = h * 0.09f, center = Offset(w * 0.76f, h * 0.2f))
        // Far hills, then the sea, then the sand.
        val hills = Path().apply {
            moveTo(0f, horizon)
            var x = 0f
            while (x <= w) { lineTo(x, horizon - h * 0.06f - h * 0.04f * sin(x / w * 9f)); x += w / 60f }
            lineTo(w, horizon); close()
        }
        drawPath(hills, Color(0xFF9DB89A))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF5FB2D6), Color(0xFFA8DDEB)), horizon, h * 0.66f), topLeft = Offset(0f, horizon), size = Size(w, h * 0.14f))
        drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF1CF), Color(0xFFFFFAEE)), h * 0.66f, h), topLeft = Offset(0f, h * 0.66f), size = Size(w, h * 0.34f))
        // Foam lines and footprints: busy, light detail along the bottom.
        for (i in 0 until 7) {
            val y = h * (0.69f + i * 0.045f)
            val wave = Path().apply {
                moveTo(0f, y)
                var x = 0f
                while (x <= w) { lineTo(x, y + h * 0.008f * sin(x / w * 23f + i)); x += w / 80f }
            }
            drawPath(wave, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = h * 0.006f))
        }
        for (i in 0 until 40) {
            val x = w * ((i * 37) % 100) / 100f
            val y = h * (0.72f + ((i * 53) % 26) / 100f)
            drawCircle(Color(0xFFD8BE8E), radius = h * 0.006f, center = Offset(x, y))
        }
        // A parasol on the left: one dark shape, so the picture is not all light.
        drawRect(Color(0xFF5A4636), topLeft = Offset(w * 0.18f, h * 0.55f), size = Size(w * 0.006f, h * 0.3f))
        drawArc(Color(0xFFE5533D), 180f, 180f, true, topLeft = Offset(w * 0.12f, h * 0.5f), size = Size(w * 0.13f, h * 0.12f))
    }

    @Composable
    fun NightScene(modifier: Modifier) = Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF070B1A), Color(0xFF1C2448))))
        drawCircle(Color(0xFFF4F1E0), radius = h * 0.07f, center = Offset(w * 0.2f, h * 0.18f))
        // City windows along the bottom: small light spots on dark blocks.
        for (b in 0 until 14) {
            val left = w * b / 14f
            val top = h * (0.55f + ((b * 29) % 25) / 100f)
            drawRect(Color(0xFF0E1122), topLeft = Offset(left, top), size = Size(w / 14f - 4f, h - top))
            for (r in 0 until 8) for (c in 0 until 3) {
                if ((b * 7 + r * 3 + c) % 4 == 0) {
                    drawRect(Color(0xFFFFD27A), topLeft = Offset(left + 8f + c * (w / 50f), top + 10f + r * h * 0.04f), size = Size(w / 90f, h * 0.018f))
                }
            }
        }
    }
}
