package app.subtitles

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Reads the two ends of the file behind [channel], by position, so a large file costs two small reads. */
internal fun FileChannel.readEnds(): FileEnds {
    val size = size()
    fun readAt(position: Long): ByteArray {
        val buffer = ByteBuffer.allocate(minOf(HASH_CHUNK_BYTES.toLong(), size).toInt())
        var at = position
        while (buffer.hasRemaining()) {
            val read = read(buffer, at)
            if (read <= 0) break
            at += read
        }
        return buffer.array().copyOf(buffer.position())
    }
    return FileEnds(size, readAt(0), readAt(maxOf(0L, size - HASH_CHUNK_BYTES)))
}
