package app.utils

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

/**
 * The half of FileKit that does not exist in a browser.
 *
 * FileKit publishes a web build, but only part of its API is in it. A `PlatformFile` there wraps
 * the browser's own `File` object, handed over by a picker, and a browser gives a page no path,
 * no way to write back to disk and no durable handle to reopen later. So the path constructor,
 * `write`, `writeString`, `exists`, the security-scoped bookmark pair and the save-dialog
 * launcher are all absent, and naming any of them in commonMain breaks the web build.
 *
 * Every function here fails loudly rather than quietly on the web, because that is what the call
 * sites already expect: each one wraps its file work in `runCatching` or checks for null, so a
 * thrown [UnsupportedOperationException] lands exactly where a permission error or a full disk
 * would, and the user gets the failure message that path already had.
 *
 * `PlatformFile.name`, `PlatformFile.path` and the picker launchers do exist on the web, so they
 * stay as direct FileKit calls and are not wrapped here.
 */

/** A file at an absolute path. Throws on the web, where a page cannot name a file on disk. */
expect fun platformFileAt(path: String): PlatformFile

/** Replaces the file's contents. Throws on the web, where a page cannot write to a picked file. */
expect suspend fun PlatformFile.writeBytesCompat(bytes: ByteArray)

/** Replaces the file's contents with text. Throws on the web, for the same reason. */
expect suspend fun PlatformFile.writeTextCompat(text: String)

/**
 * A durable handle that survives the process: an iOS security-scoped bookmark, an Android
 * persistable document URI. Throws on the web, which has no equivalent.
 */
expect suspend fun PlatformFile.durableBookmark(): ByteArray

/** Reopens what [durableBookmark] recorded. Throws on the web. */
expect suspend fun platformFileFromBookmark(bytes: ByteArray): PlatformFile

/** Whether the target is still there. Throws on the web. */
expect suspend fun PlatformFile.stillExists(): Boolean

/** Opens the platform's save dialog. */
interface FileSaver {
    /** Suggests a name and extension, then hands the chosen file to the callback, or null. */
    fun launch(suggestedName: String, extension: String)
}

/**
 * A save dialog, remembered across recompositions. [onResult] receives the chosen file, or null
 * when the person cancelled, and on the web it always receives null: a browser saves a file by
 * downloading it, which is a different gesture with no file handle at the end of it.
 */
@Composable
expect fun rememberFileSaver(onResult: (PlatformFile?) -> Unit): FileSaver
