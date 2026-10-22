package app.uicomponents

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Coil, as on Android, but the image stays static.
 *
 * On this target Coil decodes only the first frame of a GIF. A real animation needs an `<img>`
 * element over the canvas through Compose's HTML interop, the same mechanism that the web video
 * engine needs. At alpha 0 nothing loads.
 */
@Composable
actual fun AnimatedImage(
    url: String,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
    alpha: Float,
    onLoaded: (() -> Unit)?,
    onFailed: (() -> Unit)?,
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
        onError = { onFailed?.invoke() },
        modifier = modifier.alpha(alpha),
    )
}
