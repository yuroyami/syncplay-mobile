package app.uicomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The floating-readout chrome born in the volume/brightness gesture pill, extracted so every
 * piece of transient over-video chrome wears the same skin.
 *
 * This is the NON-blurred sibling of [glassSurface]: fixed dark colors per the over-video chrome
 * rule (readable on any frame, in any theme), no Haze involvement, so it is safe on chrome that
 * stays composed while video plays. The signature ingredients: a near-black vertical gradient
 * body, a hairline rim lit at the top and gone by the bottom, and a deep soft shadow.
 */

/** Chrome of the gesture pill: shadow, near-black gradient body, top-lit rim. */
fun Modifier.darkGlassPill(shape: Shape = RoundedCornerShape(26.dp)): Modifier = this
    .shadow(20.dp, shape)
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(Color(0xFF1B1B21).copy(alpha = 0.90f), Color(0xFF08080B).copy(alpha = 0.94f))
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))
        ),
        shape = shape
    )

/** The faint gradient disc the gesture pill sets its icon on. */
@Composable
fun PillIconBadge(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))
                )
            ),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}
