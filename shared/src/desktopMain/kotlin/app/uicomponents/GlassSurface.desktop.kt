package app.uicomponents

import androidx.compose.runtime.Composable

/** Not needed on desktop: KitePlayer draws its frames into Compose, so Haze already blurs video. */
@Composable
actual fun DialogBackdropBlur() = Unit
