package app.subtitles

import SyncplayMobile.shared.BuildConfig
import app.utils.getLogDirectoryPath
import app.utils.httpClient
import app.utils.loggy
import app.utils.writeTextFile
import de.jensklingenberg.ktorfit.Ktorfit
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

    /** Consumer key from local.properties (`yuroyami.keyOpenSubsApi`). */
    private val API_KEY = BuildConfig.OPENSUBTITLES_API_KEY

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        httpClient.config {
            /* Surface 4xx/5xx as ResponseException. Without this (Ktor 3 defaults to false) the
             * call validator never fires and the lenient Json below silently parses the error body
             * as an empty response, so searches come back empty with no log entry. With this on,
             * the catch block writes the real cause (e.g. 406 quota exceeded, 401 bad key). */
            expectSuccess = true

            install(ContentNegotiation) {
                json(json)
            }

            /* Re-installing DefaultRequest REPLACES the base client's defaults (same plugin key),
             * so the app-wide UA doesn't stack with this one. OpenSubtitles requires the UA to
             * identify the app as "Name vX.Y.Z" and blocks generic or comma-merged UAs. */
            defaultRequest {
                header(HttpHeaders.UserAgent, "Synkplay v${BuildConfig.APP_VERSION}")
                header("Api-Key", API_KEY)
                header(HttpHeaders.Accept, "application/json")
            }
        }
    }

    private val api: OpenSubtitlesAPI by lazy {
        Ktorfit.Builder()
            .baseUrl(BASE_URL)
            .httpClient(client)
            .build()
            .createOpenSubtitlesAPI()
    }

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

    /** Searches for subtitles by query, most-downloaded first. */
    suspend fun search(query: String, language: String = "en"): List<SubtitleResult> {
        return try {
            // Doc rules: languages lower-case, comma-separated, alphabetically sorted.
            val languages = language.split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .sorted()
                .joinToString(",")
                .ifEmpty { "en" }

            val response = api.search(query = query.trim().lowercase(), languages = languages)
            loggy("SubtitleSearch: ${response.totalCount} results for '$query' [$languages]")

            response.data.map { item ->
                SubtitleResult(
                    fileId = item.attributes.files.firstOrNull()?.fileId ?: 0,
                    filename = item.attributes.files.firstOrNull()?.fileName ?: "",
                    language = item.attributes.language,
                    releaseInfo = item.attributes.release,
                    downloadCount = item.attributes.downloadCount,
                    hearingImpaired = item.attributes.hearingImpaired
                )
            }.filter { it.fileId > 0 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loggy("SubtitleSearch error: ${e.message}")
            e.printStackTrace()
            emptyList()
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
                loggy("SubtitleSearch: no link in download response — ${info.message}")
                return SubtitleDownloadResult.Failed
            }

            /* The link is a short-lived direct URL to the UTF-8 subtitle text. */
            val subtitleContent = client.get(info.link).bodyAsText()

            val dir = getLogDirectoryPath() ?: return SubtitleDownloadResult.Failed
            // The server controls file_name — never let it traverse out of our directory.
            val filename = info.fileName.substringAfterLast('/').substringAfterLast('\\')
                .ifBlank { "subtitle_$fileId.srt" }
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
                    json.decodeFromString<OpenSubtitlesDownloadResponse>(e.response.bodyAsText())
                }.getOrNull()
                loggy("SubtitleSearch: download quota exhausted — ${quota?.message}")
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
            loggy("SubtitleSearch download error: ${e.message}")
            e.printStackTrace()
            SubtitleDownloadResult.Failed
        }
    }
}

/** Outcome of [SubtitleSearch.download], rich enough for user-facing quota messaging. */
sealed class SubtitleDownloadResult {
    /** [remaining] = downloads left in the key's daily quota window (5/day on the free plan). */
    data class Success(val path: String, val fileName: String, val remaining: Int) : SubtitleDownloadResult()

    /** Daily quota exhausted (HTTP 406). [resetTime] is human-readable, e.g. "12 hours". */
    data class QuotaExceeded(val resetTime: String) : SubtitleDownloadResult()

    object Failed : SubtitleDownloadResult()
}

data class SubtitleResult(
    val fileId: Int,
    val filename: String,
    val language: String,
    val releaseInfo: String,
    val downloadCount: Int,
    val hearingImpaired: Boolean
)
