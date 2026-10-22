package app.subtitles

import SyncplayMobile.shared.KiteBuildConfig
import app.utils.LogRedactor
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
 * Searches and downloads subtitles from the OpenSubtitles **.com** REST API through a
 * [Ktorfit]-generated [OpenSubtitlesAPI], like the Klipy client.
 */
object SubtitleSearch {
    private const val BASE_URL = "https://api.opensubtitles.com/api/v1/"

    /** Internal, not private, so that the model test checks this exact configuration, not a copy. */
    internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The app's one client, on the shared transport, saving into the subtitle cache folder. */
    private val service by lazy { SubtitleService(BASE_URL, httpClient) { getCacheDirectoryPath("subtitles") } }

    /**
     * Cleans a media file name for a subtitle search. It removes the extension, turns dots,
     * underscores, dashes, brackets and parentheses into spaces, and removes common release tags
     * (resolution, codec, source and a few release group names).
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
    suspend fun search(query: String, language: String = "en", episode: Episode? = null, moviehash: String? = null): SubtitleSearchOutcome =
        service.search(query, language, episode, moviehash)

    /** See [SubtitleService.download]. */
    suspend fun download(fileId: Int): SubtitleDownloadResult = service.download(fileId)
}

/**
 * The OpenSubtitles client itself. The app keeps one in [SubtitleSearch]. A test builds its own
 * against a local server, so it checks the request that the app really sends, without the network.
 */
internal class SubtitleService(baseUrl: String, transport: HttpClient, private val cacheDir: () -> String?) {

    /** The consumer key, from local.properties (`yuroyami.keyOpenSubsApi`). */
    private val apiKey = KiteBuildConfig.OPENSUBTITLES_API_KEY

    private val client = transport.config {
        /* Turn a 4xx or 5xx response into a ResponseException (the Ktor 3 default is false).
         * Without it, the call validator never fires, and the lenient Json below silently parses
         * the error body as an empty response: searches come back empty with no log entry. With
         * it, the catch block logs the real cause (for example 406 quota exceeded, 401 bad key). */
        expectSuccess = true

        install(ContentNegotiation) {
            json(SubtitleSearch.json)
        }

        /* Installing DefaultRequest again does not replace the base client's block. Both config
         * lambdas run in install order on one builder, so header() appends, and the User-Agent
         * would stack ("SynkplayMobile/x.y.z; Synkplay vx.y.z"). headers[] sets the value after
         * the base block runs, so it replaces the User-Agent with the exact "Name vX.Y.Z" form
         * that OpenSubtitles requires. */
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
     * Searches for subtitles by query, most downloaded first. [language] is one or more
     * comma-separated ISO 639-1 codes. The value "all" (or a blank value) drops the language
     * filter, so every language comes back. [episode] narrows the search to one episode, and
     * [moviehash] (see [openSubtitlesHash]) puts the results made for this exact file first.
     */
    suspend fun search(query: String, language: String = "en", episode: Episode? = null, moviehash: String? = null): SubtitleSearchOutcome {
        return try {
            // The API docs want the languages lower-case, comma-separated and sorted. "all" (or an
            // empty value) becomes null, which omits the filter, so the API returns every language.
            val languages: String? = language.split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && it != "all" }
                .sorted()
                .joinToString(",")
                .ifEmpty { null }

            LogRedactor.register(LogRedactor.Kind.Search, query)
            val response = api.search(
                episodeNumber = episode?.episode,
                languages = languages,
                moviehash = moviehash,
                query = query.trim().lowercase(),
                seasonNumber = episode?.season,
            )
            loggy("SubtitleSearch: ${response.totalCount} results for '$query' [${languages ?: "all"}]")

            // A hash match was made for this exact file, so it goes first.
            response.data.sortedByDescending { it.attributes.moviehashMatch }.map { item ->
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
            // A rejected key, a quota or a dead network must not look like "no results".
            loggy("SubtitleSearch error: ${e.message}")
            SubtitleSearchOutcome.Failed(e.message ?: e::class.simpleName ?: "error")
        }
    }

    /**
     * Downloads a subtitle file and saves it locally.
     *
     * On the free plan, searches are unlimited, but the API enforces a daily download quota per
     * consumer key (5 a day). The download response reports the downloads left
     * ([SubtitleDownloadResult.Success.remaining]). An exhausted quota comes back as HTTP 406,
     * which becomes [SubtitleDownloadResult.QuotaExceeded], so the UI can tell the user instead
     * of failing silently.
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
            // The server controls file_name, so the name must not point outside the cache folder.
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
            // 406 means that the daily download quota is used up. The error body still carries
            // the quota fields ({"requests":N,"remaining":0,"message":"...","reset_time":"..."}).
            if (e.response.status == HttpStatusCode.NotAcceptable) {
                val quota = runCatching {
                    SubtitleSearch.json.decodeFromString<OpenSubtitlesDownloadResponse>(e.response.bodyAsText())
                }.getOrNull()
                loggy("SubtitleSearch: download quota exhausted: ${quota?.message}")
                // Quota windows are daily. When the error body does not parse, "24 hours" is
                // better than showing "Resets in ." in the OSD.
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

/** Outcome of [SubtitleSearch.download], with enough detail for the quota messages to the user. */
sealed class SubtitleDownloadResult {
    /** [remaining] is the number of downloads left in the key's daily quota (5 on the free plan). */
    data class Success(val path: String, val fileName: String, val remaining: Int) : SubtitleDownloadResult()

    /** The daily quota is used up (HTTP 406). [resetTime] is readable text, such as "12 hours". */
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
 * The languages that the subtitle search offers, as OpenSubtitles ISO 639-1 codes.
 *
 * Codes only: the picker names each language in the app's display language, so a French reader
 * sees "Allemand", not "German". The value "all" is not here, because it is not a language.
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
