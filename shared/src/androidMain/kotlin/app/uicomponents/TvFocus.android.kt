package app.uicomponents

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

@Composable
internal actual fun Modifier.onTvTextFieldNavigationKeyEvent(
    onKeyEvent: (KeyEvent) -> Boolean,
): Modifier {
    val localView = LocalView.current
    val insetView = localView.context.findActivity()?.window?.decorView ?: localView
    val imeDismissalRequestedAt = remember { longArrayOf(0L) }

    return onPreInterceptKeyBeforeSoftKeyboard { event ->
        val imeVisible = ViewCompat.getRootWindowInsets(insetView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        val now = SystemClock.uptimeMillis()

        if (imeVisible &&
            event.type == KeyEventType.KeyDown &&
            event.key == Key.Back
        ) {
            imeDismissalRequestedAt[0] = now
            return@onPreInterceptKeyBeforeSoftKeyboard false
        }

        val imeDismissalPending = imeVisible &&
            now - imeDismissalRequestedAt[0] in 0..IME_DISMISSAL_GRACE_PERIOD_MS
        if (!imeVisible || !imeDismissalPending) {
            imeDismissalRequestedAt[0] = 0L
        }

        val shouldRoute = shouldRouteTvTextFieldNavigation(imeVisible, imeDismissalPending) &&
            event.type == KeyEventType.KeyDown &&
            tvFocusDirection(event.key) != null
        if (shouldRoute) {
            imeDismissalRequestedAt[0] = 0L
            localView.post { onKeyEvent(event) }
        }
        shouldRoute
    }
}

private const val IME_DISMISSAL_GRACE_PERIOD_MS = 2_000L

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
