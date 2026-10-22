package app.subtitles

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForReadingFromURL
import platform.Foundation.readDataOfLength
import platform.Foundation.seekToEndOfFile
import platform.Foundation.seekToFileOffset
import platform.posix.memcpy

/** Reads by offset through a file handle. The file's security scope is open while it plays. */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun PlatformFile.readEnds(): FileEnds? = withContext(Dispatchers.IO) {
    runCatching {
        val handle = NSFileHandle.fileHandleForReadingFromURL(nsUrl, null) ?: return@runCatching null
        try {
            val size = handle.seekToEndOfFile().toLong()
            handle.seekToFileOffset(0u)
            val head = handle.readDataOfLength(HASH_CHUNK_BYTES.toULong()).toByteArray()
            handle.seekToFileOffset(maxOf(0L, size - HASH_CHUNK_BYTES).toULong())
            val tail = handle.readDataOfLength(HASH_CHUNK_BYTES.toULong()).toByteArray()
            FileEnds(size, head, tail)
        } finally {
            handle.closeFile()
        }
    }.getOrNull()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
