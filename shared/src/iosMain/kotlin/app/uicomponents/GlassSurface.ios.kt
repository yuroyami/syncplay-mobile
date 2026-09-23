package app.uicomponents

import androidx.compose.runtime.Composable

/** No equivalent on iOS: Compose renders the dialog into the same surface, so there is no window
 *  behind it for the system to blur. */
@Composable
actual fun DialogBackdropBlur() = Unit

/**
 * Always true. iOS draws video in a UIKit interop view that Compose cannot capture in any case,
 * so the answer changes nothing here.
 */
actual fun videoSurfaceSupportsGlass(): Boolean = true
