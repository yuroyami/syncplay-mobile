package app.uicomponents.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.theme.palette

enum class ChevronDirection { Left, Right, Up, Down }

/** Two 1.4dp hairlines, drawn, not an icon font glyph. Square terminals like every other stroke. */
@Composable
fun Chevron(
    direction: ChevronDirection,
    modifier: Modifier = Modifier,
    color: Color = palette.inkDim,
    size: Dp = 14.dp,
    stroke: Dp = 1.4.dp,
) {
    Canvas(modifier.size(size)) {
        val s = 4.dp.toPx()
        val cx = this.size.width / 2
        val cy = this.size.height / 2
        val w = stroke.toPx()
        when (direction) {
            ChevronDirection.Left -> {
                drawLine(color, Offset(cx + s / 2, cy - s), Offset(cx - s / 2, cy), w, StrokeCap.Butt)
                drawLine(color, Offset(cx - s / 2, cy), Offset(cx + s / 2, cy + s), w, StrokeCap.Butt)
            }
            ChevronDirection.Right -> {
                drawLine(color, Offset(cx - s / 2, cy - s), Offset(cx + s / 2, cy), w, StrokeCap.Butt)
                drawLine(color, Offset(cx + s / 2, cy), Offset(cx - s / 2, cy + s), w, StrokeCap.Butt)
            }
            ChevronDirection.Up -> {
                drawLine(color, Offset(cx - s, cy + s / 2), Offset(cx, cy - s / 2), w, StrokeCap.Butt)
                drawLine(color, Offset(cx, cy - s / 2), Offset(cx + s, cy + s / 2), w, StrokeCap.Butt)
            }
            ChevronDirection.Down -> {
                drawLine(color, Offset(cx - s, cy - s / 2), Offset(cx, cy + s / 2), w, StrokeCap.Butt)
                drawLine(color, Offset(cx, cy + s / 2), Offset(cx + s, cy - s / 2), w, StrokeCap.Butt)
            }
        }
    }
}
