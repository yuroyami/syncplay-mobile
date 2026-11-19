package app.uicomponents

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.compositionLocalOf

/**
 * The corner that the window's own buttons take, where the app draws under the title bar: the
 * close, minimize and full-screen buttons of the macOS desktop app. The top bar of each screen and
 * the room's message box start after it. Zero everywhere else, and in full screen.
 */
val LocalWindowControls = compositionLocalOf { WindowInsets(0) }
