package app.uicomponents

import androidx.compose.runtime.Composable

/** No window behind a canvas to blur; the page is the window. */
@Composable
actual fun DialogBackdropBlur() = Unit

/**
 * False, and it will stay false.
 *
 * Video on the web is an HTML element the browser composites itself, outside the canvas Compose
 * draws into. There are no pixels for the blur to sample, exactly as with a SurfaceView on
 * Android, so panels over video fall back to a plain tint.
 */
actual fun videoSurfaceSupportsGlass(): Boolean = false
