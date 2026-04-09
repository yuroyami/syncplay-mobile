package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/** Displays an image from a URL with animation support for GIF and WebP on all platforms. */
@Composable
expect fun AnimatedImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
)
