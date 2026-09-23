package app.room.ui.rightcards

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

/** Only a television lacks a file picker; desktop uses the usual one, so this returns null. */
@Composable
internal actual fun rememberTvVideoPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)? = null
