package app.uicomponents

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.theme.Theming.flexibleGradient
import app.utils.isTelevision

/**
 * Marks a composable as D-pad-navigable with an animated focus indicator.
 *
 * Adds:
 *  - `Modifier.focusable()` so the element joins the focus tree
 *  - Optional [focusRequester] for programmatic focus (e.g. initial focus on screen entry)
 *  - An animated gradient border + slight scale-up while focused, drawn around the bounds
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
    onActivate: (() -> Unit)? = null,
): Modifier {
    if (!enabled) return this
    var focused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(if (focused) scaleWhenFocused else 1f, animationSpec = spring())
    val borderAlpha by animateFloatAsState(if (focused) 1f else 0f, animationSpec = spring())

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
            }
        }
        .then(
            if (isTelevision && onActivate != null) {
                Modifier.onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && isTvActivationKey(event.key)) {
                        onActivate()
                        true
                    } else {
                        false
                    }
                }
            } else {
                Modifier
            }
        )
        .then(if (addFocusable) Modifier.focusable() else Modifier)
}

/**
 * On TV, directional keys must leave a text editor and continue through the focus graph. Without
 * this, BasicTextField/TextField consume D-pad events as caret movement and trap remote users.
 */
@Composable
fun Modifier.tvTextFieldNavigation(
    enabled: Boolean = true,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    left: FocusRequester? = null,
    right: FocusRequester? = null,
): Modifier {
    if (!shouldInterceptTvTextFieldNavigation(enabled, isTelevision)) return this

    val focusManager = LocalFocusManager.current
    var pendingDirection by remember { mutableStateOf<FocusDirection?>(null) }

    LaunchedEffect(pendingDirection) {
        val direction = pendingDirection ?: return@LaunchedEffect
        val target = when (direction) {
            FocusDirection.Up -> up
            FocusDirection.Down -> down
            FocusDirection.Left -> left
            FocusDirection.Right -> right
            else -> null
        }

        target?.requestFocus()?.takeIf { it } ?: focusManager.moveFocus(direction)
        pendingDirection = null
    }

    return this
        .onTvTextFieldNavigationKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) {
                return@onTvTextFieldNavigationKeyEvent false
            }

            val direction = tvFocusDirection(event.key)
                ?: return@onTvTextFieldNavigationKeyEvent false

            pendingDirection = direction
            true
        }
}

@Composable
fun Modifier.tvFocusProperties(
    properties: FocusProperties.() -> Unit,
): Modifier = if (isTelevision) focusProperties(properties) else this

@Composable
internal expect fun Modifier.onTvTextFieldNavigationKeyEvent(
    onKeyEvent: (KeyEvent) -> Boolean,
): Modifier

internal fun shouldInterceptTvTextFieldNavigation(
    enabled: Boolean,
    isTelevision: Boolean,
): Boolean = enabled && isTelevision

internal fun shouldRouteTvTextFieldNavigation(
    imeVisible: Boolean,
    imeDismissalPending: Boolean,
): Boolean = !imeVisible || imeDismissalPending

internal fun tvFocusDirection(key: Key): FocusDirection? = when (key) {
    Key.DirectionUp -> FocusDirection.Up
    Key.DirectionDown -> FocusDirection.Down
    Key.DirectionLeft -> FocusDirection.Left
    Key.DirectionRight -> FocusDirection.Right
    else -> null
}

internal fun isTvActivationKey(key: Key): Boolean = when (key) {
    Key.DirectionCenter,
    Key.Enter,
    Key.NumPadEnter,
    Key.Spacebar -> true
    else -> false
}
