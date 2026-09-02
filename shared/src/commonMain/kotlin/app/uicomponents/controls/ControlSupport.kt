package app.uicomponents.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.theme.Motion
import app.theme.Space
import app.preferences.Preferences
import app.preferences.value
import app.theme.palette
import app.utils.platformCallback

/*
 * Shared behaviour for every drawn control: press feedback, the hover/focus/selected states,
 * the expanded touch target, and haptics. Defined once so no control invents its own.
 */

/** Press feedback without ripple: 70 percent opacity and a 1dp inset, drawn without re-layout. */
@Composable
fun Modifier.pressFeedback(interactionSource: InteractionSource, enabled: Boolean = true): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val active = pressed && enabled
    val alpha by animateFloatAsState(
        targetValue = if (active) 0.7f else 1f,
        animationSpec = if (active) snap() else Motion.quick(),
        label = "press",
    )
    return graphicsLayer {
        this.alpha = alpha
        if (active && size.width > 0f && size.height > 0f) {
            val inset = 1.dp.toPx()
            scaleX = 1f - (2 * inset) / size.width
            scaleY = 1f - (2 * inset) / size.height
        }
    }
}

/**
 * Hover, focus and selected treatments from DESIGN/FOUNDATION, drawn behind the content.
 * Hover lifts the ground 6 percent, focus 12 percent plus the gradient border inset by 1dp,
 * selected 8 percent plus a 2dp accent edge on the start side. Nothing moves or scales.
 */
@Composable
fun Modifier.controlStates(
    interactionSource: InteractionSource,
    shape: Shape,
    selected: Boolean = false,
    enabled: Boolean = true,
): Modifier {
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val p = palette
    val focusAlpha by animateFloatAsState(if (focused && enabled) 1f else 0f, Motion.quick(), label = "focus")
    val hoverAlpha by animateFloatAsState(if (hovered && enabled) 1f else 0f, Motion.quick(), label = "hover")
    val brand = p.brandField
    return drawWithContent {
        val outline = shape.createOutline(size, layoutDirection, this)
        if (selected) drawOutline(outline, p.accent.copy(alpha = 0.08f))
        if (hoverAlpha > 0f) drawOutline(outline, p.ink.copy(alpha = 0.06f * hoverAlpha))
        if (focusAlpha > 0f) drawOutline(outline, p.accent.copy(alpha = 0.12f * focusAlpha))
        drawContent()
        if (selected) {
            val w = 2.dp.toPx()
            val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - w
            drawRect(p.accent, Offset(x, 0f), Size(w, size.height))
        }
        if (focusAlpha > 0f) {
            val inset = 1.dp.toPx()
            val stroke = 2.dp.toPx()
            val inner = shape.createOutline(Size(size.width - 2 * inset, size.height - 2 * inset), layoutDirection, this)
            translate(inset, inset) {
                drawOutline(
                    outline = inner,
                    brush = Brush.linearGradient(brand.map { it.copy(alpha = it.alpha * focusAlpha) }),
                    style = Stroke(stroke),
                )
            }
        }
    }
}

/** Expands the hit area past the visual bounds, centring the content. Never overlaps a neighbour. */
fun Modifier.touchTarget(minWidth: Dp = Space.touchMin, minHeight: Dp = Space.row): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val w = maxOf(placeable.width, minWidth.roundToPx()).coerceAtMost(constraints.maxWidth)
    val h = maxOf(placeable.height, minHeight.roundToPx()).coerceAtMost(constraints.maxHeight)
    layout(w, h) {
        placeable.placeRelative((w - placeable.width) / 2, (h - placeable.height) / 2)
    }
}

/**
 * The control haptics vocabulary. One platform call today, so the three strengths are the same
 * pulse; the names exist so call sites say what they mean and the mapping can grow.
 */
object Feedback {
    fun tick() = pulse()
    fun light() = pulse()
    fun medium() = pulse()

    private fun pulse() {
        runCatching {
            if (Preferences.HAPTICS_ON_CONTROLS.value()) platformCallback.performHapticFeedback()
        }
    }
}

/** A slow light sweep for a tile still loading: ink at 6 to 14 percent, moving across. */
@Composable
fun Modifier.shimmer(): Modifier {
    val p = palette
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerPhase",
    )
    val base = p.ink.copy(alpha = 0.06f)
    val lit = p.ink.copy(alpha = 0.14f)
    return drawBehind {
        val w = size.width
        val x = (phase * 2f - 0.5f) * w
        drawRect(Brush.linearGradient(listOf(base, lit, base), start = Offset(x - w / 2, 0f), end = Offset(x + w / 2, size.height)))
    }
}
