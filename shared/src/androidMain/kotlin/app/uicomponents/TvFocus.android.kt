package app.uicomponents

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * The Android half of a remote working a text field. It runs before the soft keyboard: while the
 * keyboard is up every key is the keyboard's, and only once it is gone, or Back has just been
 * pressed to send it away, does a key reach the field's own handling. The insets keep calling the
 * keyboard visible for a moment after Back, which is what the grace period covers.
 * Written for pull request #159 and verified there on a Google TV Streamer.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun Modifier.onTvTextFieldNavigationKeyEvent(onKeyEvent: (KeyEvent) -> Boolean): Modifier {
    val localView = LocalView.current
    val insetView = localView.context.findActivity()?.window?.decorView ?: localView
    val imeDismissalRequestedAt = remember { longArrayOf(0L) }

    return onPreInterceptKeyBeforeSoftKeyboard { event ->
        val imeVisible = ViewCompat.getRootWindowInsets(insetView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        val now = SystemClock.uptimeMillis()

        if (imeVisible && event.type == KeyEventType.KeyDown && event.key == Key.Back) {
            imeDismissalRequestedAt[0] = now
            return@onPreInterceptKeyBeforeSoftKeyboard false
        }

        val imeDismissalPending = imeVisible && now - imeDismissalRequestedAt[0] in 0..IME_DISMISSAL_GRACE_PERIOD_MS
        if (!imeVisible || !imeDismissalPending) imeDismissalRequestedAt[0] = 0L
        if (!shouldRouteTvTextFieldNavigation(imeVisible, imeDismissalPending)) return@onPreInterceptKeyBeforeSoftKeyboard false

        val handled = onKeyEvent(event)
        if (handled) imeDismissalRequestedAt[0] = 0L
        handled
    }
}

/** How long after Back the insets may still call the keyboard visible before a key is trusted again. */
private const val IME_DISMISSAL_GRACE_PERIOD_MS = 2_000L

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal actual fun softKeyboardVisible(): Boolean = WindowInsets.isImeVisible
