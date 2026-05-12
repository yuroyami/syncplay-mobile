package app.uicomponents

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.theme.Theming.flexibleGradient

/**
 * Lets focusable elements report their focus state upward so the Room HUD can
 * stay visible while D-pad navigation is in progress. Default is null on screens
 * that don't gate visibility on focus (Home, Server Host, etc.).
 *
 * Provided by RoomScreenUI; the room's UI state manager increments/decrements a
 * focus counter that feeds into `hasActiveOverlay`.
 */
val LocalDpadFocusReporter = compositionLocalOf<((Boolean) -> Unit)?> { null }

/**
 * Marks a composable as D-pad-navigable with an animated focus indicator.
 *
 * Adds:
 *  - `Modifier.focusable()` so the element joins the focus tree
 *  - Optional [focusRequester] for programmatic focus (e.g. initial focus on screen entry)
 *  - An animated gradient border + slight scale-up while focused, drawn around the bounds
 *  - Upward focus reporting via [LocalDpadFocusReporter] so the room HUD can hold itself open
 *
 * The visual indicator uses the same gradient brush as the rest of the app so focused
 * elements feel native to the design language.
 */
@Composable
fun Modifier.tvFocusable(
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
    borderWidth: Dp = 2.dp,
    scaleWhenFocused: Float = 1.04f,
    addFocusable: Boolean = true,
): Modifier {
    if (!enabled) return this
    val reporter = LocalDpadFocusReporter.current
    var focused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(if (focused) scaleWhenFocused else 1f, animationSpec = spring())
    val borderAlpha by animateFloatAsState(if (focused) 1f else 0f, animationSpec = spring())

    DisposableEffect(reporter) {
        onDispose { if (focused) reporter?.invoke(false) }
    }

    return this
        .scale(scale)
        .then(
            if (borderAlpha > 0f) Modifier.border(
                width = borderWidth,
                brush = Brush.linearGradient(colors = flexibleGradient.map { it.copy(alpha = borderAlpha) }),
                shape = shape,
            ) else Modifier
        )
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { state ->
            val newFocused = state.isFocused || state.hasFocus
            if (newFocused != focused) {
                focused = newFocused
                reporter?.invoke(newFocused)
            }
        }
        .then(if (addFocusable) Modifier.focusable() else Modifier)
}
