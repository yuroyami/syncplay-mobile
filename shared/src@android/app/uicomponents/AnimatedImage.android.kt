package app.uicomponents

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

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
    // At alpha 0, compose nothing, so a GIF behind a hidden HUD does not decode every frame for a
    // surface nobody can see. The empty Box keeps the tile's space, so the grid does not move.
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
