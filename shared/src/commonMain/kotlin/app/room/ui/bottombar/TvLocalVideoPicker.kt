package app.room.ui.bottombar

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

@Composable
internal expect fun rememberTvLocalVideoPickerLauncher(
    onVideoSelected: (PlatformFile) -> Unit,
): (() -> Unit)?

internal fun shouldUseTvLocalVideoPicker(
    launcherAvailable: Boolean,
): Boolean = launcherAvailable
