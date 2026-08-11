package app.room.ui.bottombar

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

@Composable
internal actual fun rememberTvLocalVideoPickerLauncher(
    onVideoSelected: (PlatformFile) -> Unit,
): (() -> Unit)? = null
