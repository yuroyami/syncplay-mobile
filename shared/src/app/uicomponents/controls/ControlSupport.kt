package app.uicomponents.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

/*
 * Shared behaviour for every drawn control: press feedback, the hover, focus and selected states,
 * the expanded touch target, and haptics. It is defined once, so no control invents its own.
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
 * The focus ring for controls on a surface painted with the brand gradient, where the usual
 * gradient ring would vanish into its ground. Null everywhere else, which means the gradient ring.
 */
val LocalFocusRing = staticCompositionLocalOf<Brush?> { null }

/**
 * Draws the hover, focus and selected states of a control. Hover lays a 6 percent ink wash, focus
 * a 12 percent accent wash and a 2dp ring inset by 1dp (the brand gradient unless [focusRing]
 * names another), and selected an 8 percent accent wash and a 2dp accent edge on the start side.
 * The washes sit behind the content, and the ring and the edge sit over it. Nothing moves or
 * scales.
 */
@Composable
fun Modifier.controlStates(
    interactionSource: InteractionSource,
    shape: Shape,
    selected: Boolean = false,
    enabled: Boolean = true,
    focusRing: Brush? = LocalFocusRing.current,
): Modifier {
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val p = palette
    val focusAlpha by animateFloatAsState(if (focused && enabled) 1f else 0f, Motion.quick(), label = "focus")
    val hoverAlpha by animateFloatAsState(if (hovered && enabled) 1f else 0f, Motion.quick(), label = "hover")
    val brand = p.brandField
    // The selected state is spoken as well as drawn, so a screen reader can tell which playlist
    // item is playing, which track is chosen or which theme is on. Every control that draws a
    // selected state comes through here, so this one line covers all of them.
    // The outlines, the stroke and the full-strength ring are made once for each size, not on
    // every draw. The animated alphas are read only while drawing, so a fade redraws and nothing more.
    return semantics { this.selected = selected }.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val edge = 2.dp.toPx()
        val edgeX = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - edge
        val inset = 1.dp.toPx()
        val stroke = Stroke(2.dp.toPx())
        val inner = shape.createOutline(Size(size.width - 2 * inset, size.height - 2 * inset), layoutDirection, this)
        val fullRing = focusRing ?: Brush.linearGradient(brand)
        onDrawWithContent {
            if (selected) drawOutline(outline, p.accent.copy(alpha = 0.08f))
            if (hoverAlpha > 0f) drawOutline(outline, p.ink.copy(alpha = 0.06f * hoverAlpha))
            if (focusAlpha > 0f) drawOutline(outline, p.accent.copy(alpha = 0.12f * focusAlpha))
            drawContent()
            if (selected) drawRect(p.accent, Offset(edgeX, 0f), Size(edge, size.height))
            if (focusAlpha > 0f) {
                translate(inset, inset) {
                    when {
                        focusRing != null -> drawOutline(outline = inner, brush = focusRing, alpha = focusAlpha, style = stroke)
                        focusAlpha == 1f -> drawOutline(outline = inner, brush = fullRing, style = stroke)
                        // Mid-fade, the gradient's own colours carry the alpha.
                        else -> drawOutline(
                            outline = inner,
                            brush = Brush.linearGradient(brand.map { it.copy(alpha = it.alpha * focusAlpha) }),
                            style = stroke,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Expands the hit area past the visual bounds, centring the content. Never overlaps a neighbour.
 *
 * Both defaults are the platform minimum (48dp). Do not default the height to the 42dp row
 * height, which is below that minimum.
 */
fun Modifier.touchTarget(minWidth: Dp = Space.touchMin, minHeight: Dp = Space.touchMin): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val w = maxOf(placeable.width, minWidth.roundToPx()).coerceAtMost(constraints.maxWidth)
    val h = maxOf(placeable.height, minHeight.roundToPx()).coerceAtMost(constraints.maxHeight)
    layout(w, h) {
        placeable.placeRelative((w - placeable.width) / 2, (h - placeable.height) / 2)
    }
}

/**
 * The haptic feedback of controls, when the haptics setting is on. The platform has one call, so
 * the three strengths give the same pulse. The names exist so call sites say what they mean and
 * the mapping can grow.
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
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerPhase",
    )
    val base = p.ink.copy(alpha = 0.06f)
    val lit = p.ink.copy(alpha = 0.14f)
    return drawBehind {
        val (start, end) = shimmerSweep(phase, size.width, size.height)
        drawRect(Brush.linearGradient(listOf(base, lit, base), start = start, end = end))
    }
}

/**
 * Where the light band's gradient starts and ends at [phase] (0 to 1). The band runs diagonally,
 * so a corner stays inside the band long after the band's centre has left the tile. The sweep
 * starts and ends one full diagonal reach past the edges, which hides the jump from phase 1 back
 * to 0.
 */
internal fun shimmerSweep(phase: Float, width: Float, height: Float): Pair<Offset, Offset> {
    if (width <= 0f) return Offset.Zero to Offset(0f, height)
    val reach = (width * width + height * height) / width
    val startX = -reach + phase * 2f * reach
    return Offset(startX, 0f) to Offset(startX + width, height)
}
