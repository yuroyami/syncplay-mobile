package app.room.ui.bottombar

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberTvVideoPreDownloader(
    onProgress: (Float?) -> Unit,
    onComplete: (String) -> Unit,
    onError: (String) -> Unit,
): ((String) -> Unit)? = null
