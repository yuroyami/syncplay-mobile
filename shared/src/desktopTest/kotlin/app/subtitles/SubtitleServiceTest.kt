package app.subtitles

import app.utils.httpClient
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The requests the app really sends to OpenSubtitles, answered by a local server instead of the
 * live one. The API redirects a query string that is not in sorted order, and a downloaded file's
 * name comes from the server, so both are pinned here. The replies follow the documented shapes.
 */
class SubtitleServiceTest {

    /** One request as the server saw it. Header names are lower-cased. */
    private class Seen(val method: String, val path: String, val query: String?, val headers: Map<String, List<String>>, val body: String) {
        val names: List<String> get() = query.orEmpty().split('&').filter { it.isNotEmpty() }.map { it.substringBefore('=') }

        fun param(name: String): String? = query.orEmpty().split('&').firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=')?.let { URLDecoder.decode(it, Charsets.UTF_8) }
    }

    private lateinit var server: HttpServer
    private lateinit var cache: File
    private val seen = CopyOnWriteArrayList<Seen>()
    private var downloadStatus = 200
    private var downloadName = "Big.Buck.Bunny.2008.1080p.srt"

    private val base get() = "http://127.0.0.1:${server.address.port}"

    private fun service() = SubtitleService("$base/api/v1/", httpClient) { cache.absolutePath }

    @BeforeTest
    fun start() {
        cache = Files.createTempDirectory("subtitle-cache").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { answer(it) }
        server.start()
    }

    @AfterTest
    fun stop() {
        server.stop(0)
        cache.deleteRecursively()
    }

    private fun answer(exchange: HttpExchange) {
        val uri = exchange.requestURI
        seen += Seen(
            method = exchange.requestMethod,
            path = uri.path,
            query = uri.rawQuery,
            headers = exchange.requestHeaders.entries.associate { (name, values) -> name.lowercase() to values.toList() },
            body = exchange.requestBody.readBytes().decodeToString(),
        )
        val (status, reply) = when (uri.path) {
            "/api/v1/subtitles" -> 200 to SEARCH_RESPONSE
            "/api/v1/download" -> downloadStatus to if (downloadStatus == 200) downloadResponse() else QUOTA_RESPONSE
            "/files/sub.srt" -> 200 to SUBTITLE_TEXT
            else -> 404 to "{}"
        }
        val bytes = reply.encodeToByteArray()
        exchange.responseHeaders.add("Content-Type", if (uri.path.endsWith(".srt")) "text/plain; charset=utf-8" else "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun downloadResponse() = """
        {"link": "$base/files/sub.srt", "file_name": ${jsonString(downloadName)}, "requests": 1, "remaining": 4,
         "message": "Your quota will be renewed in 23 hours", "reset_time": "23 hours and 59 minutes",
         "reset_time_utc": "2030-01-02T00:00:00.000Z"}
    """.trimIndent()

    private fun jsonString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun `search sends its parameters in sorted order`(): Unit = runBlocking {
        val outcome = service().search("  Big Buck Bunny ", "FR, en")
        val request = seen.single()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/subtitles", request.path)
        // Any other order is answered with a redirect: one wasted round trip on every search.
        assertEquals(listOf("languages", "order_by", "order_direction", "page", "query"), request.names)
        assertEquals("en,fr", request.param("languages"))
        assertEquals("big buck bunny", request.param("query"))
        assertIs<SubtitleSearchOutcome.Results>(outcome)
    }

    @Test
    fun `all languages drops the language filter`(): Unit = runBlocking {
        service().search("x", "all")
        assertEquals(listOf("order_by", "order_direction", "page", "query"), seen.single().names)
    }

    @Test
    fun `every request carries the key and one exact user agent`(): Unit = runBlocking {
        service().search("x")
        val headers = seen.single().headers
        // The shared client has its own agent, and a stacked one is refused by the API.
        val agent = headers["user-agent"].orEmpty()
        assertEquals(1, agent.size, "one User-Agent header, not ${agent.size}")
        assertTrue(Regex("""Synkplay v[^\s;,]+""").matches(agent.single()), "the agent must be exactly 'Synkplay vX.Y.Z', got '${agent.single()}'")
        assertEquals(1, headers["api-key"].orEmpty().size, "one Api-Key header")
    }

    @Test
    fun `search maps a row and skips a row with no file`(): Unit = runBlocking {
        val rows = assertIs<SubtitleSearchOutcome.Results>(service().search("x")).items
        assertEquals(1, rows.size)
        with(rows.single()) {
            assertEquals(1234567, fileId)
            assertEquals("Big.Buck.Bunny.2008.1080p.srt", filename)
            assertEquals("en", language)
            assertEquals("Big.Buck.Bunny.2008.1080p.BluRay.x264", releaseInfo)
            assertEquals(5120, downloadCount)
            assertTrue(hearingImpaired)
        }
    }

    @Test
    fun `download posts the file id as JSON and saves inside the cache folder`(): Unit = runBlocking {
        downloadName = "../../outside/escape.srt"
        val saved = assertIs<SubtitleDownloadResult.Success>(service().download(1234567))
        val post = seen.first { it.path == "/api/v1/download" }
        assertEquals("POST", post.method)
        assertTrue(post.headers["content-type"].orEmpty().any { it.startsWith("application/json") }, "a body with no JSON type dies before sending")
        assertEquals("""{"file_id":1234567}""", post.body)
        assertEquals(File(cache, "escape.srt").canonicalPath, File(saved.path).canonicalPath)
        assertEquals(SUBTITLE_TEXT, File(saved.path).readText())
        assertEquals(4, saved.remaining)
    }

    @Test
    fun `no file name from the server can leave the cache folder`(): Unit = runBlocking {
        val cases = mapOf("""..\..\escape.srt""" to "escape.srt", ".." to "subtitle_7.srt", "." to "subtitle_7.srt", "" to "subtitle_7.srt")
        for ((sent, kept) in cases) {
            downloadName = sent
            val saved = assertIs<SubtitleDownloadResult.Success>(service().download(7), "file_name '$sent'")
            assertEquals(kept, saved.fileName, "file_name '$sent'")
            assertEquals(cache.canonicalPath, File(saved.path).canonicalFile.parent, "file_name '$sent'")
        }
    }

    @Test
    fun `an exhausted quota reads its reset time from the error body`(): Unit = runBlocking {
        downloadStatus = 406
        assertEquals(SubtitleDownloadResult.QuotaExceeded("12 hours and 3 minutes"), service().download(7))
    }
}

/** A search reply in the documented shape: one usable row, one with no file, and keys the app does not model. */
private val SEARCH_RESPONSE = """
{
  "total_pages": 1, "total_count": 2, "per_page": 60, "page": 1,
  "data": [
    {
      "id": "7654321", "type": "subtitle",
      "attributes": {
        "subtitle_id": "7654321", "language": "en",
        "download_count": 5120, "new_download_count": 12,
        "hearing_impaired": true, "hd": true, "fps": 24.0, "votes": 3, "ratings": 6.5,
        "from_trusted": true, "foreign_parts_only": false, "upload_date": "2019-03-02T11:23:45Z",
        "ai_translated": false, "machine_translated": false,
        "release": "Big.Buck.Bunny.2008.1080p.BluRay.x264",
        "uploader": {"uploader_id": 12, "name": "someone", "rank": "trusted"},
        "feature_details": {"feature_id": 1001, "feature_type": "Movie", "year": 2008, "title": "Big Buck Bunny"},
        "files": [{"file_id": 1234567, "cd_number": 1, "file_name": "Big.Buck.Bunny.2008.1080p.srt"}]
      }
    },
    {
      "id": "7654322", "type": "subtitle",
      "attributes": {"subtitle_id": "7654322", "language": "fr", "download_count": 3, "release": "no file attached", "files": []}
    }
  ]
}
""".trimIndent()

private const val QUOTA_RESPONSE =
    """{"requests": 5, "remaining": 0, "message": "You have downloaded your allowed 5 subtitles for 24h", "reset_time": "12 hours and 3 minutes", "reset_time_utc": "2030-01-02T00:00:00.000Z"}"""

private const val SUBTITLE_TEXT = "1\n00:00:01,000 --> 00:00:02,500\nHello there.\n"
