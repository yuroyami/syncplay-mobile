package app.player.kite

import android.util.Log
import app.utils.contextObtainer
import app.utils.playableUri
import io.github.vinceglb.filekit.PlatformFile
import java.io.File
import java.io.FileInputStream

/**
 * Turns a picked file into something that KitePlayer (the FFmpeg-based engine) can open.
 *
 * A picker returns a `content://` URI, which FFmpeg's `file` protocol cannot open. There are two
 * ways around that, in order of preference:
 *
 *  1. A real filesystem path, when the provider has one AND this process can read it. This is the
 *     best case: FFmpeg opens the file itself, seeks natively, and the descriptor closes at once.
 *  2. Otherwise the descriptor itself, through the `fd:` protocol with the number passed as a
 *     pre-open option. FFmpeg `dup()`s it and never opens anything again, and its `fstat` marks a
 *     regular file as seekable.
 *
 * `/proc/self/fd/N` through the `file` protocol is NOT used on purpose, although it is the usual
 * trick and mpv's own fallback. It opens the file again by path, and the kernel checks permissions
 * against that path. On a real device, a SAF descriptor that this process may read then fails with
 * `fmt_open_input: Permission denied (code=-13)`, while the descriptor itself stays valid. The
 * `fd:` protocol uses the same descriptor without opening the file again.
 *
 * The descriptor in case 2 must outlive the open call. That is why the returned [KiteMediaPath]
 * holds it, and why [KiteImpl] releases the previous path only after the next one is installed.
 */
internal object AndroidKiteMediaResolver : KiteMediaResolver {
    override fun resolve(file: PlatformFile): KiteMediaPath? {
        val uri = file.playableUri
        when (uri.scheme) {
            // Already a real path: nothing to hold open.
            "file" -> return uri.path?.let { KiteMediaPath(it) }
            "content" -> Unit
            // http and other schemes go to FFmpeg as they are. KitePlayer's FFmpeg build decides
            // which of them it can open.
            else -> return KiteMediaPath(uri.toString())
        }

        val descriptor = runCatching {
            contextObtainer().applicationContext.contentResolver.openFileDescriptor(uri, "r")
        }.getOrNull() ?: run {
            Log.e("KitePlayer", "content resolver refused a descriptor for $uri")
            return null
        }

        realPathOf(descriptor.fd)?.let { real ->
            // FFmpeg opens this path itself, so the descriptor is no longer needed.
            runCatching { descriptor.close() }
            return KiteMediaPath(real)
        }

        // The URL must be exactly "fd:". The number goes in a pre-open option, as FFmpeg requires:
        // fd_open refuses a number in the URL with an error.
        return KiteMediaPath(
            uri = "fd:",
            openOptions = mapOf("fd" to descriptor.fd.toString()),
            releaseAction = { runCatching { descriptor.close() } },
        )
    }
}

/**
 * The real filesystem path behind an open descriptor, or null when there is none (a pipe, a
 * document served by a remote provider, a deleted file). A one-byte read confirms access, because
 * a canonical path that exists is not always a path this process may open.
 */
private fun realPathOf(fd: Int): String? = runCatching {
    val candidate = File("/proc/self/fd/$fd").canonicalPath
    if (candidate.startsWith("/proc")) return@runCatching null
    val asFile = File(candidate)
    if (!asFile.canRead()) return@runCatching null
    FileInputStream(asFile).use { it.read() }
    candidate
}.getOrNull()
