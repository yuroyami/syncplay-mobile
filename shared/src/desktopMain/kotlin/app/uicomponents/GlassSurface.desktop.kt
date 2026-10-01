package app.uicomponents

import androidx.compose.runtime.Composable

/** Not needed on desktop: KitePlayer draws its frames into Compose, so Haze already blurs video. */
@Composable
actual fun DialogBackdropBlur() = Unit

/** Desktop draws video into the Compose canvas already, so there is no second surface type to pick. */
actual fun videoSurfaceSupportsGlass(): Boolean = true

/** Always true: Skia runs the liquid glass shaders on the desktop. */
actual fun liquidGlassSupported(): Boolean = true
