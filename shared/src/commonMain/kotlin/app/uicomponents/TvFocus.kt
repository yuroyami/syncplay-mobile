package app.uicomponents

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.theme.Motion
import app.theme.Radius
import app.theme.palette

/**
 * Joins a composite element (the engine wheel) to the focus tree and shows focus without moving
 * anything: a 2dp brand border inside the bounds and the ground lifted to the accent at 12
 * percent. Nothing scales, so a focused item in a list or a clipped container stays whole. The
 * state survives an enabled flip because nothing returns early.
 */
@Composable
fun Modifier.tvFocusable(
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    shape: Shape = Radius.controlShape,
    borderWidth: Dp = 2.dp,
    addFocusable: Boolean = true,
): Modifier {
    val p = palette
    var focused by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (focused && enabled) 1f else 0f, Motion.quick(), label = "tvFocus")
    return this
        .then(
            if (alpha > 0f) Modifier
                .background(p.accent.copy(alpha = 0.12f * alpha), shape)
                .border(borderWidth, Brush.linearGradient(p.brandField.map { it.copy(alpha = alpha) }), shape)
            else Modifier
        )
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { state -> focused = state.isFocused || state.hasFocus }
        .then(if (addFocusable && enabled) Modifier.focusable() else Modifier)
}
