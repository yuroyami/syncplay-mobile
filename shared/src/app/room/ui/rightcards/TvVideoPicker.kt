package app.room.ui.rightcards

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

/**
 * Returns the action that opens a list of the videos on a television, or null where the usual
 * file picker works. A television has no file picker app. The system answers "You don't have an
 * app that can do this", so the usual picker cannot work there (pull request #163).
 */
@Composable
internal expect fun rememberTvVideoPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)?
