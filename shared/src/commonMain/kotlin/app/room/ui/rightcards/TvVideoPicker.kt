package app.room.ui.rightcards

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

/**
 * A television has no file picker app: the system answers "You don't have an app that can do this",
 * so the usual picker is a dead end there (pull request #163). This lists the device's own videos
 * instead, and returns the action that opens that list, or null where the usual picker works.
 */
@Composable
internal expect fun rememberTvVideoPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)?
