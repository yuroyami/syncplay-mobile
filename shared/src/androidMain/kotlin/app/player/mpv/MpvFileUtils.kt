package app.player.mpv

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.util.Log
import app.utils.loggy
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object MpvFileUtils {
    fun copyAssets(context: Context) {
        val assetManager = context.assets
        // subfont.ttf is not copied here: installMpvSubfontIfNeeded() (commonMain) installs it
        // from a shared Compose resource. cacert.pem stays in assets because it must exist when
        // an mpv core starts (tls-ca-file), before playback.
        val files = arrayOf("cacert.pem")
        val configDir = context.filesDir.path
        for (filename in files) {
            var ins: InputStream? = null
            var out: OutputStream? = null
            try {
                ins = assetManager.open(filename, AssetManager.ACCESS_STREAMING)
                val outFile = File("$configDir/$filename")
                // available() returns only an estimate for generic streams, but an asset stream
                // returns the full file size.
                if (outFile.length() == ins.available().toLong()) {
                    loggy("Skipping copy of asset file (exists same size): $filename")
                    continue
                }
                out = FileOutputStream(outFile)
                ins.copyTo(out)
                loggy("Copied asset file: $filename")
            } catch (e: IOException) {
                loggy("Failed to copy asset file: $filename")
                loggy(e)
            } finally {
                ins?.close()
                out?.close()
            }
        }
    }

    fun Context.resolveUri(data: Uri): String? {
        val filepath = when (data.scheme) {
            "file" -> data.path
            "content" -> openContentFd(this, data)
            "http", "https", "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh", "tcp", "udp"
                -> data.toString()

            else -> null
        }

        if (filepath == null)
            Log.e("mpv", "unknown scheme: ${data.scheme}")
        return filepath
    }

    private fun openContentFd(context: Context, uri: Uri): String? {
        val resolver = context.applicationContext.contentResolver
        Log.e("mpv", "Resolving content URI: $uri")

        val desc = try {
            resolver.openFileDescriptor(uri, "r") ?: return null
        } catch (e: Exception) {
            Log.e("mpv", "Failed to open content fd: $e")
            return null
        }

        // Try the real path first. If there is one, close the descriptor and return the path.
        val path = findRealPath(desc.fd) // .fd, not detached, for the check
        if (path != null) {
            Log.e("mpv", "Found real file path: $path")
            desc.close() // Safe to close: mpv opens the real path itself.
            return path
        }

        /* No real path: detach the descriptor and hand it to mpv.
         *
         * Use fdclose://, not fd://. mpv does not take ownership of an fd:// descriptor:
         * stream_file.c borrows it and closes only the fdclose:// form. With fd://, every SAF
         * file opened this way leaks a descriptor, and a process has a limited number of them. */
        val fd = desc.detachFd()
        return "fdclose://${fd}"
    }

    private fun findRealPath(fd: Int): String? {
        var ins: InputStream? = null
        try {
            val path = File("/proc/self/fd/${fd}").canonicalPath
            if (!path.startsWith("/proc") && File(path).canRead()) {
                // Confirm access with a real read.
                ins = FileInputStream(path)
                ins.read()
                return path
            }
        } catch (_: Exception) {
        } finally {
            ins?.close()
        }
        return null
    }
}