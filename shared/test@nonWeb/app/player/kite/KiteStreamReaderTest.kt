package app.player.kite

import io.github.yuroyami.kiteplayer.MediaIo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The reader behind a YouTube stream. A short forward seek reads through the open stream, because
 * a new request for every video chunk that an audio scan skips made the scan ten times slower. The
 * label stays the same for the same video, so the visualizer finds the study it already made.
 */
class KiteStreamReaderTest {

    private val source = ByteArray(4096) { it.toByte() }

    /** Bytes from memory that count the seeks, and can answer "nothing yet" a number of times. */
    private class BytesIo(private val bytes: ByteArray, private var nothingYet: Int = 0) : MediaIo {
        var at = 0L
        var seeks = 0
        override val size: Long get() = bytes.size.toLong()
        override val seekable: Boolean = true

        override suspend fun read(into: ByteArray, offset: Int, length: Int): Int {
            if (nothingYet > 0) {
                nothingYet--
                return 0
            }
            if (at >= bytes.size) return -1
            val count = minOf(length.toLong(), bytes.size - at).toInt()
            bytes.copyInto(into, offset, at.toInt(), at.toInt() + count)
            at += count
            return count
        }

        override suspend fun seek(position: Long) {
            seeks++
            at = position
        }

        override fun close() = Unit
    }

    private suspend fun MediaIo.readExactly(count: Int): ByteArray {
        val out = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val read = read(out, filled, count - filled)
            check(read > 0) { "stream ended after $filled bytes" }
            filled += read
        }
        return out
    }

    @Test
    fun aShortForwardSeekReadsThroughTheOpenStream() = runTest {
        val upstream = BytesIo(source)
        val io = ReadThroughMediaIo(upstream, readThroughBytes = 1024)
        io.readExactly(16)
        io.seek(16 + 1000)
        assertContentEquals(source.copyOfRange(1016, 1024), io.readExactly(8))
        assertEquals(0, upstream.seeks)
    }

    @Test
    fun aFarForwardSeekOpensTheStreamAgain() = runTest {
        val upstream = BytesIo(source)
        val io = ReadThroughMediaIo(upstream, readThroughBytes = 1024)
        io.readExactly(16)
        io.seek(16 + 1025)
        assertContentEquals(source.copyOfRange(1041, 1049), io.readExactly(8))
        assertEquals(1, upstream.seeks)
    }

    @Test
    fun aBackwardSeekOpensTheStreamAgain() = runTest {
        val upstream = BytesIo(source)
        val io = ReadThroughMediaIo(upstream, readThroughBytes = 1024)
        io.readExactly(100)
        io.seek(10)
        assertContentEquals(source.copyOfRange(10, 20), io.readExactly(10))
        assertEquals(1, upstream.seeks)
    }

    @Test
    fun theEndDuringAReadThroughIsTheEnd() = runTest {
        val io = ReadThroughMediaIo(BytesIo(source.copyOf(100)), readThroughBytes = 1024)
        io.seek(150)
        assertEquals(-1, io.read(ByteArray(8), 0, 8))
    }

    @Test
    fun nothingYetDuringAReadThroughIsNothingYet() = runTest {
        val io = ReadThroughMediaIo(BytesIo(source, nothingYet = 1), readThroughBytes = 1024)
        io.seek(500)
        assertEquals(0, io.read(ByteArray(8), 0, 8))
        assertContentEquals(source.copyOfRange(500, 508), io.readExactly(8))
    }

    @Test
    fun aYouTubeFileStreamIsLabelledByVideoAndFormat() {
        val stream = "https://rr2---sn-abc.googlevideo.com/videoplayback?expire=1&itag=18&source=youtube"
        assertEquals("youtube:dQw4w9WgXcQ/18", youTubeStreamLabel(stream, "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("youtube:dQw4w9WgXcQ/140", youTubeStreamLabel("https://rr2---sn-abc.googlevideo.com/videoplayback?itag=140", "https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun anythingElseKeepsItsOwnAddress() {
        val watch = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        // A live stream's manifest, another host, no page link, no format, a page that is not YouTube.
        assertNull(youTubeStreamLabel("https://manifest.googlevideo.com/api/manifest/hls_variant/id/1", watch))
        assertNull(youTubeStreamLabel("https://example.com/videoplayback?itag=18", watch))
        assertNull(youTubeStreamLabel("https://rr2---sn-abc.googlevideo.com/videoplayback?itag=18", null))
        assertNull(youTubeStreamLabel("https://rr2---sn-abc.googlevideo.com/videoplayback?expire=1", watch))
        assertNull(youTubeStreamLabel("https://rr2---sn-abc.googlevideo.com/videoplayback?itag=18", "https://soundcloud.com/a/b"))
    }
}
