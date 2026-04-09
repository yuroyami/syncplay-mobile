package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
actual fun AnimatedImage(
    url: String,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}
