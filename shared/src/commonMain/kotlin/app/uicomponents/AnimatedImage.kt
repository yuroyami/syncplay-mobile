package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * Displays an image from a URL with animation support for GIF and WebP on all platforms.
 *
 * @param alpha Opacity from 0f to 1f. On iOS this is forwarded to the native `UIImageView.alpha`
 *   because Compose's `Modifier.alpha` does not propagate into UIKit interop layers — applying
 *   it via the modifier chain leaves the underlying `UIView` fully opaque, producing the "ghost
 *   rectangle" / "black hole" artifact when the HUD fades out. Pass alpha as a parameter to fade
 *   the actual native view.
 */
@Composable
expect fun AnimatedImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
)
