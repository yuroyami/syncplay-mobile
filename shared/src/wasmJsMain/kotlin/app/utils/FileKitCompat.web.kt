package app.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile

/**
 * A browser has no filesystem for any of these functions. Each one throws
 * UnsupportedOperationException, which the call sites already catch with `runCatching`.
 */
private fun noFilesystem(what: String): Nothing =
    throw UnsupportedOperationException("$what is not available in a browser.")

actual fun platformFileAt(path: String): PlatformFile = noFilesystem("Opening a file by path")

actual suspend fun PlatformFile.writeBytesCompat(bytes: ByteArray): Unit =
    noFilesystem("Writing to a file")

actual suspend fun PlatformFile.writeTextCompat(text: String): Unit =
    noFilesystem("Writing to a file")

actual suspend fun PlatformFile.writeFilesCompat(paths: List<String>): Unit =
    noFilesystem("Writing to a file")

actual suspend fun PlatformFile.durableBookmark(): ByteArray =
    noFilesystem("A durable file handle")

actual suspend fun platformFileFromBookmark(bytes: ByteArray): PlatformFile =
    noFilesystem("Reopening a file from a stored handle")

actual suspend fun PlatformFile.stillExists(): Boolean =
    noFilesystem("Checking whether a file is still there")

/**
 * Always answers null.
 *
 * A browser saves by downloading, which gives the app no handle to write into, so there is
 * nothing to return. A real download (a blob from the bytes plus a click on a synthetic link)
 * does not fit this contract and needs its own function.
 */
@Composable
actual fun rememberFileSaver(onResult: (PlatformFile?) -> Unit): FileSaver = remember(onResult) {
    object : FileSaver {
        override fun launch(suggestedName: String, extension: String) {
            loggy("A save dialog was requested for $suggestedName.$extension, which a browser has no equivalent for.")
            onResult(null)
        }
    }
}
