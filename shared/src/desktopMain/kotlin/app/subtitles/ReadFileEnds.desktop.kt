package app.subtitles

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun PlatformFile.readEnds(): FileEnds? = withContext(Dispatchers.IO) {
    runCatching { RandomAccessFile(path, "r").use { it.channel.readEnds() } }.getOrNull()
}
