package app.uicomponents.controls

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import app.theme.palette

/**
 * A 2dp bar along the top rim of its container. Determinate with [progress] in 0 to 1, or a 30
 * percent segment sweeping when null. No spinners anywhere in the app.
 */
@Composable
fun ProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val p = palette
    val transition = rememberInfiniteTransition(label = "progress")
    val sweep by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .then(if (progress != null) Modifier.progressSemantics(progress.coerceIn(0f, 1f)) else Modifier.progressSemantics()),
    ) {
        drawRect(p.trackOff)
        if (progress != null) {
            drawRect(p.accent, Offset.Zero, Size(size.width * progress.coerceIn(0f, 1f), size.height))
        } else {
            val w = size.width * 0.3f
            val x = size.width * sweep
            drawRect(p.accent, Offset(x, 0f), Size(w, size.height))
        }
    }
}
