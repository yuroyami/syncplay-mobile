package app.subtitles

import SyncplayMobile.shared.KiteBuildConfig
import app.utils.getCacheDirectoryPath
import app.utils.httpClient
import app.utils.loggy
import app.utils.writeTextFile
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Searches and downloads subtitles from the OpenSubtitles **.com** REST API via a
 * [Ktorfit]-generated [OpenSubtitlesAPI] (same pattern as the Klipy client).
 */
object SubtitleSearch {
    private const val BASE_URL = "https://api.opensubtitles.com/api/v1/"

    /** Internal, not private: the model test pins THIS configuration rather than a copy of it. */
    internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The app's one client, on the shared transport, saving into the subtitle cache folder. */
    private val service by lazy { SubtitleService(BASE_URL, httpClient) { getCacheDirectoryPath("subtitles") } }

    /**
     * Cleans a media filename for subtitle searching.
     * Strips extension, replaces dots/underscores/dashes with spaces,
     * and removes common release group tags.
     */
    fun cleanMediaName(filename: String): String {
        return filename
            .substringBeforeLast('.')
            .replace(Regex("[._\\-\\[\\]()]+"), " ")
            .replace(Regex("\\b(720p|1080p|2160p|4k|x264|x265|h264|h265|hevc|aac|bluray|brrip|webrip|web-dl|hdtv|dvdrip|yts|yify|rarbg|eztv)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    /** See [SubtitleService.search]. */
    suspend fun search(query: String, language: String = "en"): SubtitleSearchOutcome = service.search(query, language)

    /** See [SubtitleService.download]. */
    suspend fun download(fileId: Int): SubtitleDownloadResult = service.download(fileId)
}

/**
 * The OpenSubtitles client itself. The app keeps one in [SubtitleSearch]; a test builds its own
 * against a local server, so the request the app really sends is checked without the network.
 */
internal class SubtitleService(baseUrl: String, transport: HttpClient, private val cacheDir: () -> String?) {

    /** Consumer key from local.properties (`yuroyami.keyOpenSubsApi`). */
    private val apiKey = KiteBuildConfig.OPENSUBTITLES_API_KEY

    private val client = transport.config {
        /* Surface 4xx/5xx as ResponseException. Without this (Ktor 3 defaults to false) the
         * call validator never fires and the lenient Json below silently parses the error body
         * as an empty response, so searches come back empty with no log entry. With this on,
         * the catch block writes the real cause (e.g. 406 quota exceeded, 401 bad key). */
        expectSuccess = true

        install(ContentNegotiation) {
            json(SubtitleSearch.json)
        }

        /* Re-installing DefaultRequest does NOT replace the base client's block: both config
         * lambdas run in install order on one builder, so header() APPENDS and the UA would
         * stack ("SynkplayMobile/x.y.z; Synkplay vx.y.z", observed in the wire log). headers[]
         * (set) runs after the base block and overwrites its UA with the exact "Name vX.Y.Z"
         * form OpenSubtitles requires. */
        defaultRequest {
            headers[HttpHeaders.UserAgent] = "Synkplay v${KiteBuildConfig.APP_VERSION}"
            header("Api-Key", apiKey)
            header(HttpHeaders.Accept, "application/json")
        }
    }

    private val api: OpenSubtitlesAPI = Ktorfit.Builder()
        .baseUrl(baseUrl)
        .httpClient(client)
        .build()
        .createOpenSubtitlesAPI()

    /**
     * Searches for subtitles by query, most-downloaded first. [language] is one or more
     * comma-separated ISO 639-1 codes; the sentinel "all" (or a blank value) drops the language
     * filter so every language is returned.
     */
    suspend fun search(query: String, language: String = "en"): SubtitleSearchOutcome {
        return try {
            // Doc rules: languages lower-case, comma-separated, alphabetically sorted. "all" (or
            // empty) becomes null, which omits the filter, so the API returns every language.
            val languages: String? = language.split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && it != "all" }
                .sorted()
                .joinToString(",")
                .ifEmpty { null }

            val response = api.search(query = query.trim().lowercase(), languages = languages)
            loggy("SubtitleSearch: ${response.totalCount} results for '$query' [${languages ?: "all"}]")

            response.data.map { item ->
                SubtitleResult(
                    fileId = item.attributes.files.firstOrNull()?.fileId ?: 0,
                    filename = item.attributes.files.firstOrNull()?.fileName ?: "",
                    language = item.attributes.language,
                    releaseInfo = item.attributes.release,
                    downloadCount = item.attributes.downloadCount,
                    hearingImpaired = item.attributes.hearingImpaired
                )
            }.filter { it.fileId > 0 }.let { SubtitleSearchOutcome.Results(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A rejected key, a quota, a dead network: each used to look like "no results".
            loggy("SubtitleSearch error: ${e.message}")
            SubtitleSearchOutcome.Failed(e.message ?: e::class.simpleName ?: "error")
        }
    }

    /**
     * Downloads a subtitle file and saves it locally.
     *
     * Note the free-plan economics: searches are unlimited, but the API enforces a
     * DAILY DOWNLOAD QUOTA per consumer key (5/day on the free plan). The download
     * response reports [SubtitleDownloadResult.Success.remaining]; blowing the quota
     * comes back as HTTP 406, surfaced as [SubtitleDownloadResult.QuotaExceeded] so
     * the UI can tell the user instead of failing silently.
     */
    suspend fun download(fileId: Int): SubtitleDownloadResult {
        return try {
            val info = api.requestDownload(OpenSubtitlesDownloadRequest(fileId = fileId))
            loggy("SubtitleSearch: download link acquired, quota remaining=${info.remaining} (resets ${info.resetTime})")
            if (info.link.isEmpty()) {
                loggy("SubtitleSearch: no link in download response: ${info.message}")
                return SubtitleDownloadResult.Failed
            }

            /* The link is a short-lived direct URL to the UTF-8 subtitle text. */
            val subtitleContent = client.get(info.link).bodyAsText()

            // The cache, not the log folder: a log export must never carry subtitle files along.
            val dir = cacheDir() ?: return SubtitleDownloadResult.Failed
            // The server controls file_name, so nothing it sends may leave our directory.
            val filename = info.fileName.substringAfterLast('/').substringAfterLast('\\')
                .takeUnless { it.isBlank() || it == "." || it == ".." } ?: "subtitle_$fileId.srt"
            val path = "$dir/$filename"
            // Overwrite, not append: appending would concatenate two copies of the same
            // subtitle, which players parse as one broken cue.
            writeTextFile(path, subtitleContent)
            SubtitleDownloadResult.Success(path = path, fileName = filename, remaining = info.remaining)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClientRequestException) {
            // 406 = daily download quota exhausted. The error body still carries the quota
            // fields ({"requests":N,"remaining":0,"message":"...","reset_time":"..."}).
            if (e.response.status == HttpStatusCode.NotAcceptable) {
                val quota = runCatching {
                    SubtitleSearch.json.decodeFromString<OpenSubtitlesDownloadResponse>(e.response.bodyAsText())
                }.getOrNull()
                loggy("SubtitleSearch: download quota exhausted: ${quota?.message}")
                // Quota windows are daily; if the error body didn't parse, "24 hours" beats
                // rendering "Resets in ." in the OSD.
                SubtitleDownloadResult.QuotaExceeded(
                    resetTime = quota?.resetTime?.ifBlank { null } ?: "24 hours"
                )
            } else {
                loggy("SubtitleSearch download error: ${e.message}")
                SubtitleDownloadResult.Failed
            }
        } catch (e: Exception) {
            loggy(e)
            SubtitleDownloadResult.Failed
        }
    }
}

/** Outcome of [SubtitleSearch.search]: the rows, or why there are none. */
sealed class SubtitleSearchOutcome {
    data class Results(val items: List<SubtitleResult>) : SubtitleSearchOutcome()
    data class Failed(val reason: String) : SubtitleSearchOutcome()
}

/** Outcome of [SubtitleSearch.download], rich enough for user-facing quota messaging. */
sealed class SubtitleDownloadResult {
    /** [remaining] = downloads left in the key's daily quota window (5/day on the free plan). */
    data class Success(val path: String, val fileName: String, val remaining: Int) : SubtitleDownloadResult()

    /** Daily quota exhausted (HTTP 406). [resetTime] is human-readable, e.g. "12 hours". */
    data class QuotaExceeded(val resetTime: String) : SubtitleDownloadResult()

    data object Failed : SubtitleDownloadResult()
}

data class SubtitleResult(
    val fileId: Int,
    val filename: String,
    val language: String,
    val releaseInfo: String,
    val downloadCount: Int,
    val hearingImpaired: Boolean
)

/**
 * The languages the subtitle search offers, as OpenSubtitles ISO 639-1 codes.
 *
 * Codes only. The names used to be an English table, so a French reader picking a subtitle
 * language read "German" in an otherwise French app; the picker names them in the display
 * language instead. The "all" sentinel is not here because it is not a language.
 */
val subtitleSearchLanguageCodes: List<String> = listOf(
    "ar",
    "bn",
    "bg",
    "ca",
    "zh",
    "hr",
    "cs",
    "da",
    "nl",
    "en",
    "et",
    "fi",
    "fr",
    "de",
    "el",
    "he",
    "hi",
    "hu",
    "is",
    "id",
    "it",
    "ja",
    "ko",
    "lv",
    "lt",
    "ms",
    "no",
    "fa",
    "pl",
    "pt",
    "pt-br",
    "ro",
    "ru",
    "sr",
    "sk",
    "sl",
    "es",
    "sv",
    "ta",
    "te",
    "th",
    "tr",
    "uk",
    "ur",
    "vi",
)
