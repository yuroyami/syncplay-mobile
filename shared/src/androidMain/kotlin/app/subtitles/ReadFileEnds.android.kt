package app.subtitles

import android.net.Uri
import app.utils.contextObtainer
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import java.io.FileInputStream
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A picked file is a content URI, opened by descriptor; an app-owned file is a plain path. */
actual suspend fun PlatformFile.readEnds(): FileEnds? = withContext(Dispatchers.IO) {
    runCatching {
        val location = path
        if (location.startsWith("content://")) {
            contextObtainer().contentResolver.openFileDescriptor(Uri.parse(location), "r")?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { it.readEnds() }
            }
        } else {
            RandomAccessFile(location, "r").use { it.channel.readEnds() }
        }
    }.getOrNull()
}
