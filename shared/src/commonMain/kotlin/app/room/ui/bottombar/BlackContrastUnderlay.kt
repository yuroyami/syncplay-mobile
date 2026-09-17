package app.room.ui.bottombar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.room.roomTopInsets

/**
 * Shading under the transport so white glyphs survive bright video: black rising to 70 percent
 * over the bottom 96dp. A gradient reads as shading; a flat block read as a letterbox bar.
 */
@Composable
fun BlackContrastUnderlay(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val h = 96.dp.toPx().coerceAtMost(size.height)
                val top = size.height - h
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        startY = top,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, h),
                )
            }
    )
}

/**
 * The same shading for the top row: black at 60 percent under the notch and the status line,
 * gone 96dp below them, so the room name and the rail survive a white frame.
 */
@Composable
fun TopContrastUnderlay(modifier: Modifier = Modifier) {
    val notch = roomTopInsets().getTop(LocalDensity.current)
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val h = (notch + 96.dp.toPx()).coerceAtMost(size.height)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                        startY = 0f,
                        endY = h,
                    ),
                    size = Size(size.width, h),
                )
            }
    )
}
