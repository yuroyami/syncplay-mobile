package app.uicomponents

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import app.utils.contextObtainer
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** Matches the in-app blur radius, so the video and the UI behind a popup blur by the same amount. */
private const val BLUR_BEHIND_RADIUS = 48

/**
 * Whether video should be drawn into the view hierarchy so glass can sample it.
 *
 * TextureView is what makes glass over video possible, and it costs a GPU copy of every frame.
 * Below Android 12 there is no RenderEffect to blur with, and a low-RAM device should not pay the
 * copy either, so both get SurfaceView and its overlay plane whatever the glass setting says.
 */
actual fun videoSurfaceSupportsGlass(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val activityManager = contextObtainer().getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    if (activityManager?.isLowRamDevice == true) return false
    return true
}

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
        onDispose {
            // Leaving the flag set kept the platform blurring behind a dialog that no longer wanted it.
            if (window != null && supported) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = 0 }
            }
        }
    }
}
