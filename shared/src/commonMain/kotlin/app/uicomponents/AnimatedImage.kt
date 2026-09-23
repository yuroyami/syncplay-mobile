package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * Shows an image from a URL, with animation for GIF and WebP on all platforms.
 *
 * @param alpha Opacity from 0f to 1f. Pass the opacity here, not through `Modifier.alpha`: on
 *   iOS, `Modifier.alpha` does not reach a UIKit interop view, so the native `UIView` stays fully
 *   opaque and leaves a visible rectangle when the room's HUD (its overlay controls) fades out. iOS
 *   forwards this value to `UIImageView.alpha`.
 * @param onFailed Called when the download or the decode fails, so a loading placeholder can stop.
 */
@Composable
expect fun AnimatedImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    onLoaded: (() -> Unit)? = null,
    onFailed: (() -> Unit)? = null,
)
