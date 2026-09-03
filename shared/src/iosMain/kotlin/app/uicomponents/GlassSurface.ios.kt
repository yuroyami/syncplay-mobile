package app.uicomponents

import androidx.compose.runtime.Composable

/** No equivalent on iOS: Compose renders the dialog into the same surface, so there is no window
 *  behind it for the system to blur. */
@Composable
actual fun DialogBackdropBlur() = Unit

/** iOS draws video through a UIKit interop view that Compose cannot capture either way, so this is moot here. */
actual fun videoSurfaceSupportsGlass(): Boolean = true
