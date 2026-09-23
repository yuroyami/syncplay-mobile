package app.uicomponents

import androidx.compose.runtime.Composable

/** No window behind a canvas to blur; the page is the window. */
@Composable
actual fun DialogBackdropBlur() = Unit

/**
 * Always false.
 *
 * Video on the web is an HTML element that the browser composites itself, outside the canvas that
 * Compose draws into. The blur has no pixels to sample, as with a SurfaceView on Android, so
 * panels over video use a plain tint.
 */
actual fun videoSurfaceSupportsGlass(): Boolean = false
