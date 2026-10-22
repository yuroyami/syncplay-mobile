package app.room.ui.rightcards

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

/** Only a television lacks a file picker; the browser's own picker works, so this returns null. */
@Composable
internal actual fun rememberTvVideoPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)? = null
