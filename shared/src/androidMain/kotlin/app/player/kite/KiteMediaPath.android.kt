package app.player.kite

import android.util.Log
import app.utils.contextObtainer
import app.utils.playableUri
import io.github.vinceglb.filekit.PlatformFile
import java.io.File
import java.io.FileInputStream

/**
 * Android's answer to "what can FFmpeg open".
 *
 * A picker hands back a `content://` URI, which FFmpeg's `file` protocol cannot open. Two answers,
 * in preference order:
 *
 *  1. A real filesystem path, when the provider is backed by one AND this process can actually read
 *     it. Best case: FFmpeg opens it itself, seeks natively, and the descriptor is closed at once.
 *  2. Otherwise the descriptor itself, as the `fd:` protocol with the number passed as a pre-open
 *     option. FFmpeg `dup()`s it and never re-opens anything, and its `fstat` marks a regular file
 *     seekable.
 *
 * `/proc/self/fd/N` through the `file` protocol is deliberately NOT used, though it is the usual
 * trick and mpv's own fallback. It re-opens by PATH, and the kernel rechecks permissions against
 * that path: on a real device a SAF descriptor this process may read gave
 * `fmt_open_input: Permission denied (code=-13)` while the descriptor stayed perfectly valid. The
 * `fd:` protocol is the version of the same idea that does not re-open.
 *
 * The descriptor in case 2 must outlive the open call, which is why this holds it and why
 * [KiteImpl] releases the previous path only after the next one is installed.
 */
internal object AndroidKiteMediaResolver : KiteMediaResolver {
    override fun resolve(file: PlatformFile): KiteMediaPath? {
        val uri = file.playableUri
        when (uri.scheme) {
            // Already a real path: nothing to hold open.
            "file" -> return uri.path?.let { KiteMediaPath(it) }
            "content" -> Unit
            // http and friends go to FFmpeg verbatim; the profile decides what it can reach.
            else -> return KiteMediaPath(uri.toString())
        }

        val descriptor = runCatching {
            contextObtainer().applicationContext.contentResolver.openFileDescriptor(uri, "r")
        }.getOrNull() ?: run {
            Log.e("KitePlayer", "content resolver refused a descriptor for $uri")
            return null
        }

        realPathOf(descriptor.fd)?.let { real ->
            // FFmpeg will open this path itself, so the descriptor has done its job.
            runCatching { descriptor.close() }
            return KiteMediaPath(real)
        }

        // The URL must be exactly "fd:"; the number travels as a pre-open option, which is
        // FFmpeg's own contract (fd_open refuses a number in the URL and says so).
        return KiteMediaPath(
            uri = "fd:",
            openOptions = mapOf("fd" to descriptor.fd.toString()),
            releaseAction = { runCatching { descriptor.close() } },
        )
    }
}

/**
 * The real filesystem path behind an open descriptor, or null when there is none (a pipe, a
 * document served by a remote provider, a deleted file). Readability is confirmed with an actual
 * read, because a canonical path that exists is not necessarily a path this process may open.
 */
private fun realPathOf(fd: Int): String? = runCatching {
    val candidate = File("/proc/self/fd/$fd").canonicalPath
    if (candidate.startsWith("/proc")) return@runCatching null
    val asFile = File(candidate)
    if (!asFile.canRead()) return@runCatching null
    FileInputStream(asFile).use { it.read() }
    candidate
}.getOrNull()
