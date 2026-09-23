package app.room.ui.rightcards

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

/** Always null: iOS has the usual file picker, and only a television lacks one. */
@Composable
internal actual fun rememberTvVideoPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)? = null
