package app.uicomponents

import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** Matches the in-app blur radius, so the video and the UI behind a popup blur by the same amount. */
private const val BLUR_BEHIND_RADIUS = 48

@Composable
actual fun DialogBackdropBlur() {
    val view = LocalView.current

    val enabled = glassEnabled()

    DisposableEffect(view, enabled) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val supported = enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            window?.windowManager?.isCrossWindowBlurEnabled == true

        if (window != null && supported) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = BLUR_BEHIND_RADIUS }
        }
        onDispose { }
    }
}
