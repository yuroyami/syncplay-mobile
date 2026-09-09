package app.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.fromBookmarkData
import io.github.vinceglb.filekit.write
import io.github.vinceglb.filekit.writeString

/* Every one of these is FileKit's own call, unchanged. The indirection exists for the web. */

actual fun platformFileAt(path: String): PlatformFile = PlatformFile(path)

actual suspend fun PlatformFile.writeBytesCompat(bytes: ByteArray) = write(bytes)

actual suspend fun PlatformFile.writeTextCompat(text: String) = writeString(text)

actual suspend fun PlatformFile.durableBookmark(): ByteArray = bookmarkData().bytes

actual suspend fun platformFileFromBookmark(bytes: ByteArray): PlatformFile =
    PlatformFile.fromBookmarkData(bytes)

actual suspend fun PlatformFile.stillExists(): Boolean = exists()

@Composable
actual fun rememberFileSaver(onResult: (PlatformFile?) -> Unit): FileSaver {
    val launcher = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault(),
        onResult = onResult,
    )
    return remember(launcher) {
        object : FileSaver {
            override fun launch(suggestedName: String, extension: String) {
                launcher.launch(suggestedName = suggestedName, extension = extension)
            }
        }
    }
}
