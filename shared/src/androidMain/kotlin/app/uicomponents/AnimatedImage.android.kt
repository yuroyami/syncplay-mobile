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
) {
    // A GIF behind a hidden HUD kept decoding every frame for a surface nobody could see. At
    // alpha 0 nothing is composed at all; the tile's own space is kept so the grid does not move.
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
