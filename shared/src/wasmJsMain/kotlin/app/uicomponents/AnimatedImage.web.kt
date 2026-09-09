package app.uicomponents

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Coil, same as Android, and static for now.
 *
 * Coil decodes the first frame of a GIF on this target and stops there. Animating it properly
 * means an `<img>` element placed over the canvas through Compose's HTML interop, which is the
 * same mechanism the video engine needs, so both are worth doing at once rather than twice.
 */
@Composable
actual fun AnimatedImage(
    url: String,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
    alpha: Float,
    onLoaded: (() -> Unit)?,
) {
    if (alpha <= 0f) {
        Box(modifier)
        return
    }
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = contentScale,
        onSuccess = { onLoaded?.invoke() },
        modifier = modifier.alpha(alpha),
    )
}
