package app.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile

/**
 * None of this exists in a browser, and each function says so the way the call sites already
 * handle: by throwing, into the `runCatching` that was already there.
 */
private fun noFilesystem(what: String): Nothing =
    throw UnsupportedOperationException("$what is not available in a browser.")

actual fun platformFileAt(path: String): PlatformFile = noFilesystem("Opening a file by path")

actual suspend fun PlatformFile.writeBytesCompat(bytes: ByteArray): Unit =
    noFilesystem("Writing to a file")

actual suspend fun PlatformFile.writeTextCompat(text: String): Unit =
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
 * A browser saves by downloading, which produces no handle the app can then write into, so there
 * is nothing to hand back. Wiring a real download later means building a blob from the bytes and
 * clicking a synthetic link, which is a different shape from this contract and would be its own
 * function rather than a fill-in here.
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
