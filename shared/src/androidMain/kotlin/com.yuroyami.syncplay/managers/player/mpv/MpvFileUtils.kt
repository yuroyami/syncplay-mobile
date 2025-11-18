package com.yuroyami.syncplay.managers.player.mpv

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.yuroyami.syncplay.utils.loggy
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object MpvFileUtils {
    fun copyAssets(context: Context) {
        val assetManager = context.assets
        val files = arrayOf("subfont.ttf", "cacert.pem")
        val configDir = context.filesDir.path
        for (filename in files) {
            var ins: InputStream? = null
            var out: OutputStream? = null
            try {
                ins = assetManager.open(filename, AssetManager.ACCESS_STREAMING)
                val outFile = File("$configDir/$filename")
                // Note that .available() officially returns an *estimated* number of bytes available
                // this is only true for generic streams, asset streams return the full file size
                if (outFile.length() == ins.available().toLong()) {
                    loggy("Skipping copy of asset file (exists same size): $filename")
                    continue
                }
                out = FileOutputStream(outFile)
                ins.copyTo(out)
                loggy("Copied asset file: $filename")
            } catch (e: IOException) {
                e.printStackTrace()
                loggy("Failed to copy asset file: $filename")
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
        val fd = try {
            val desc = resolver.openFileDescriptor(uri, "r")
            desc!!.detachFd()
        } catch (e: Exception) {
            Log.e("mpv", "Failed to open content fd: $e")
            return null
        }
        // See if we skip the indirection and read the real file directly
        val path = findRealPath(fd)
        if (path != null) {
            Log.e("mpv", "Found real file path: $path")
            ParcelFileDescriptor.adoptFd(fd).close() // we don't need that anymore
            return path
        }
        // Else, pass the fd to mpv
        return "fd://${fd}"
    }

    private fun findRealPath(fd: Int): String? {
        var ins: InputStream? = null
        try {
            val path = File("/proc/self/fd/${fd}").canonicalPath
            if (!path.startsWith("/proc") && File(path).canRead()) {
                // Double check that we can read it
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