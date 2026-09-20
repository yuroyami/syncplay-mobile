package app.room.ui.rightcards

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

/** Only a television lacks a file picker; here the usual one works. */
@Composable
internal actual fun rememberTvVideoPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)? = null
