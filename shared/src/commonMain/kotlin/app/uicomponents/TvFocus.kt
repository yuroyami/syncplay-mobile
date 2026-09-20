package app.uicomponents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

/**
 * Whether the app runs on a television. Provided once at the root, so composables never ask the
 * platform themselves and the render harness can set it in a test.
 */
val LocalIsTelevision = staticCompositionLocalOf { false }

/**
 * The margin a television keeps clear at the edge of the picture. Older sets cut the outer few
 * percent away (overscan), and a set that does not still rounds its corners over the picture, so
 * nothing a person must read or reach belongs outside this.
 */
fun tvOverscan(): WindowInsets = WindowInsets(left = 48.dp, top = 27.dp, right = 48.dp, bottom = 27.dp)

/**
 * Holds [content] inside [tvOverscan] on a television, and changes nothing anywhere else. The
 * background stays behind it, full width, so the margin shows the theme's own ground rather than
 * a black band.
 */
@Composable
fun TvSafeArea(content: @Composable () -> Unit) {
    if (!LocalIsTelevision.current) {
        content()
        return
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(tvOverscan())) { content() }
}

/**
 * On a television, the remote works a text field the way Android TV apps expect:
 * - Center calls [onCenter], which is where `Field` starts editing and the keyboard opens.
 * - A printable key from a hardware keyboard calls [onType], so a paired keyboard just types.
 *   While [editing], Left and Right stay with the caret; only Up and Down leave.
 * - A direction leaves the field instead of moving the caret, so a remote can never be trapped.
 * - While the keyboard is showing, every key is the keyboard's, because that is how a remote picks
 *   letters.
 *
 * A caller can name the control a direction leads to; otherwise the focus system searches that way.
 * Off a television this is the identity modifier. The mechanism comes from pull request #159.
 */
@Composable
fun Modifier.tvTextFieldNavigation(
    enabled: Boolean = true,
    editing: Boolean = false,
    onCenter: (() -> Unit)? = null,
    onType: ((String) -> Unit)? = null,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    left: FocusRequester? = null,
    right: FocusRequester? = null,
): Modifier {
    if (!shouldInterceptTvTextFieldNavigation(enabled, LocalIsTelevision.current)) return this

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val center by rememberUpdatedState(onCenter)
    val type by rememberUpdatedState(onType)
    var pending by remember { mutableStateOf<FocusDirection?>(null) }

    // Moved in an effect, not inside the key dispatch: moving focus from within it reenters the focus system.
    LaunchedEffect(pending) {
        val direction = pending ?: return@LaunchedEffect
        val explicit = when (direction) {
            FocusDirection.Up -> up
            FocusDirection.Down -> down
            FocusDirection.Left -> left
            FocusDirection.Right -> right
            else -> null
        }
        val moved = explicit != null && runCatching { explicit.requestFocus() }.getOrDefault(false)
        if (!moved) focusManager.moveFocus(direction)
        pending = null
    }

    return onTvTextFieldNavigationKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onTvTextFieldNavigationKeyEvent false
        if (event.key == Key.DirectionCenter) {
            val action = center ?: return@onTvTextFieldNavigationKeyEvent false
            action()
            keyboard?.show()
            return@onTvTextFieldNavigationKeyEvent true
        }
        val typed = printableText(event)
        if (typed != null) {
            val action = type ?: return@onTvTextFieldNavigationKeyEvent false
            action(typed)
            return@onTvTextFieldNavigationKeyEvent true
        }
        val direction = tvFocusDirection(event.key) ?: return@onTvTextFieldNavigationKeyEvent false
        if (editing && (direction == FocusDirection.Left || direction == FocusDirection.Right)) return@onTvTextFieldNavigationKeyEvent false
        pending = direction
        true
    }
}

/**
 * Where the platform decides whether a key reaches [onKeyEvent] at all. Android has a soft keyboard
 * to defer to and a moment after Back when the insets still call it visible; the other platforms
 * have nothing between the key and the field. [onKeyEvent] returns whether it took the key.
 */
@Composable
internal expect fun Modifier.onTvTextFieldNavigationKeyEvent(onKeyEvent: (KeyEvent) -> Boolean): Modifier

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

/** The text a hardware key types, or null for a key that types nothing or carries a shortcut modifier. */
internal fun printableText(event: KeyEvent): String? {
    if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return null
    val code = event.utf16CodePoint
    if (code < 0x20 || code == 0x7F) return null
    return code.toChar().toString()
}

/** The keys a remote or a keyboard uses to press the focused control. */
internal fun isTvActivationKey(key: Key): Boolean = when (key) {
    Key.DirectionCenter,
    Key.Enter,
    Key.NumPadEnter,
    Key.Spacebar -> true
    else -> false
}

/** Whether the soft keyboard is showing. Visibility, not height: a television keyboard floats and reports none. */
@Composable
internal expect fun softKeyboardVisible(): Boolean
