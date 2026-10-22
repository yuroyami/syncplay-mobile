package app.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.fromBookmarkData
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.source
import io.github.vinceglb.filekit.write
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.writeString

/* Each function calls FileKit directly. The wrappers exist only for the web build, which has no
 * filesystem. */

actual fun platformFileAt(path: String): PlatformFile = PlatformFile(path)

actual suspend fun PlatformFile.writeBytesCompat(bytes: ByteArray) = write(bytes)

actual suspend fun PlatformFile.writeTextCompat(text: String) = writeString(text)

actual suspend fun PlatformFile.writeFilesCompat(paths: List<String>) = withContext(ioDispatcher) {
    sink().buffered().use { out ->
        for (path in paths) {
            out.writeString("=== ${path.substringAfterLast('/')} ===\n")
            PlatformFile(path).source().buffered().use { it.transferTo(out) }
            out.writeString("\n")
        }
    }
}

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
