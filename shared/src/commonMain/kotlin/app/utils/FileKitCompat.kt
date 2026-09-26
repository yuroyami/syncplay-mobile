package app.utils

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

/**
 * The part of FileKit that the web build of FileKit does not have.
 *
 * FileKit's web build has only part of its API. There a `PlatformFile` wraps the browser's own
 * `File` object from a picker. A browser gives a page no path, no way to write back to disk and
 * no durable handle to reopen a file later. So the path constructor, `write`, `writeString`,
 * `exists`, the security-scoped bookmark pair and the save-dialog launcher are absent, and a
 * call to any of them in commonMain breaks the web build.
 *
 * On the web, every function here except [rememberFileSaver] throws instead of failing silently.
 * Each call site already wraps its file work in `runCatching` or checks for null. So a thrown
 * [UnsupportedOperationException] lands where a permission error or a full disk would, and the
 * user sees the failure message that the call site already has.
 *
 * `PlatformFile.name`, `PlatformFile.path` and the picker launchers exist on the web, so the code
 * calls FileKit directly for them.
 */

/** A file at an absolute path. Throws on the web, where a page cannot name a file on disk. */
expect fun platformFileAt(path: String): PlatformFile

/** Replaces the file's contents. Throws on the web, where a page cannot write to a picked file. */
expect suspend fun PlatformFile.writeBytesCompat(bytes: ByteArray)

/** Replaces the file's contents with [text]. Throws on the web, like [writeBytesCompat]. */
expect suspend fun PlatformFile.writeTextCompat(text: String)

/**
 * Replaces the file's contents with the files at [paths], each under a line with its name. It
 * copies one file at a time through a small buffer. Throws on the web, like [writeBytesCompat].
 */
expect suspend fun PlatformFile.writeFilesCompat(paths: List<String>)

/**
 * A durable handle that survives the process: an iOS security-scoped bookmark, an Android
 * persistable document URI. Throws on the web, which has no equivalent.
 */
expect suspend fun PlatformFile.durableBookmark(): ByteArray

/** Reopens what [durableBookmark] recorded. Throws on the web. */
expect suspend fun platformFileFromBookmark(bytes: ByteArray): PlatformFile

/** Whether the file still exists. Throws on the web. */
expect suspend fun PlatformFile.stillExists(): Boolean

/** Opens the platform's save dialog. */
interface FileSaver {
    /** Opens the dialog with a suggested name and extension. The result goes to `onResult`. */
    fun launch(suggestedName: String, extension: String)
}

/**
 * A save dialog, remembered across recompositions. [onResult] receives the chosen file, or null
 * when the user cancels. On the web it always receives null, because a browser saves a file as
 * a download, which gives no file handle.
 */
@Composable
expect fun rememberFileSaver(onResult: (PlatformFile?) -> Unit): FileSaver
