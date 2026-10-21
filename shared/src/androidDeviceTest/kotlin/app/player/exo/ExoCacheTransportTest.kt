package app.player.exo

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ExoCacheTransportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun reopeningTheSameUrlCannotReadThePreviousLoadsCache() {
        val uri = Uri.parse("https://example.com/video.mp4")
        assertNotEquals(ExoRollingCache(context, uri).cacheKey, ExoRollingCache(context, uri).cacheKey)
    }

    @Test
    fun committingAPartialFragmentPublishesBytesAndResumesAtTheRightOffset() {
        val directory = File(context.cacheDir, "cache-test-${UUID.randomUUID()}")
        val cache = SimpleCache(directory, NoOpCacheEvictor(), StandaloneDatabaseProvider(context))
        val key = "test-video"
        val hole = checkNotNull(cache.startReadWriteNonBlocking(key, 0, 2L * 1024 * 1024))
        val sink = CommittingCacheDataSink(CacheDataSink(cache, ExoMediaCache.FRAGMENT_BYTES))
        val bytes = ByteArray(624_640) { it.toByte() }
        try {
            sink.open(DataSpec.Builder().setUri("https://example.com/video.mp4").setKey(key)
                .setLength(2L * 1024 * 1024).setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION).build())
            sink.write(bytes, 0, bytes.size)
            assertTrue(cache.getCachedLength(key, 0, bytes.size.toLong()) < 0)
            sink.commit()
            assertEquals(bytes.size.toLong(), cache.getCachedLength(key, 0, bytes.size.toLong()))
            sink.commit() // Remaining paused must not reopen a file or duplicate the fragment.
            sink.write(bytes, 0, bytes.size)
            sink.commit()
            assertEquals(2L * bytes.size, cache.getCachedLength(key, 0, 2L * bytes.size))
            val stored = cache.getCachedSpans(key).flatMap { checkNotNull(it.file).readBytes().asIterable() }
            assertContentEquals(bytes + bytes, stored.toByteArray())
        } finally {
            sink.close()
            cache.releaseHoleSpan(hole)
            cache.release()
            directory.deleteRecursively()
        }
    }

    @Test
    fun anIgnoredRangeIsRejectedBeforeReadingTheResponseBody() {
        // No body is sent. An implementation that skips the prefix inside open() fails.
        val headers = withHttpResponse("HTTP/1.1 200 OK\r\nContent-Length: 5000000000\r\n\r\n") { url ->
            val source = rangeSource()
            try {
                assertFailsWith<RangeNotSupportedException> { source.open(rangeSpec(url)) }
            } finally {
                source.close()
            }
        }
        assertEquals("range: bytes=4000000000-4000000007",
            headers.first { it.startsWith("Range:", ignoreCase = true) }.lowercase())
    }

    @Test
    fun aValidPartialResponseReturnsTheRequestedBytesWithoutSkippingThem() {
        withHttpResponse("HTTP/1.1 206 Partial Content\r\nContent-Length: 8\r\n" +
            "Content-Range: bytes 4000000000-4000000007/5000000000\r\n\r\nABCDEFGH") { url ->
            val source = rangeSource()
            try {
                assertEquals(8L, source.open(rangeSpec(url)))
                val bytes = ByteArray(8)
                var count = 0
                while (count < bytes.size) {
                    val read = source.read(bytes, count, bytes.size - count)
                    assertTrue(read > 0)
                    count += read
                }
                assertContentEquals("ABCDEFGH".toByteArray(), bytes)
                assertEquals(-1, source.read(bytes, 0, bytes.size))
            } finally {
                source.close()
            }
        }
    }

    private fun rangeSource() = ValidatingRangeDataSource(DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(5_000).setReadTimeoutMs(5_000).createDataSource(), 5_000_000_000)

    private fun rangeSpec(url: String) = DataSpec.Builder().setUri(url)
        .setPosition(4_000_000_000).setLength(8).build()

    private fun withHttpResponse(response: String, block: (String) -> Unit): List<String> {
        val executor = Executors.newSingleThreadExecutor()
        try {
            return ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 5_000
                val request = executor.submit<List<String>> {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val reader = socket.getInputStream().bufferedReader()
                        val headers = buildList {
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                add(line)
                            }
                        }
                        socket.getOutputStream().apply {
                            write(response.toByteArray())
                            flush()
                        }
                        headers
                    }
                }
                block("http://127.0.0.1:${server.localPort}/video.mp4")
                request.get(5, TimeUnit.SECONDS)
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
