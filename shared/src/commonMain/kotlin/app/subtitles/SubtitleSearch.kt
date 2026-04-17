package app.subtitles

import app.utils.getLogDirectoryPath
import app.utils.httpClient
import app.utils.loggy
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Searches and downloads subtitles from the OpenSubtitles API.
 */
object SubtitleSearch {
    private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
    private const val API_KEY = "iesFjGxVcXtBMnEbxMRYyWbU3M1UEaaL"

    private val client by lazy {
        httpClient.config {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
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

    /** Searches for subtitles by query. */
    suspend fun search(query: String, language: String = "en"): List<SubtitleResult> {
        return try {
            val response = client.get("$BASE_URL/subtitles") {
                url {
                    parameters.append("query", query)
                    parameters.append("languages", language)
                    parameters.append("order_by", "download_count")
                    parameters.append("order_direction", "desc")
                }
                header("Api-Key", API_KEY)
                header("User-Agent", "Syncplay-Mobile v1.0")
            }
            val body = response.body<OpenSubtitlesSearchResponse>()
            body.data.map { item ->
                SubtitleResult(
                    fileId = item.attributes.files.firstOrNull()?.fileId ?: 0,
                    filename = item.attributes.files.firstOrNull()?.fileName ?: "",
                    language = item.attributes.language,
                    releaseInfo = item.attributes.release,
                    downloadCount = item.attributes.downloadCount,
                    hearingImpaired = item.attributes.hearingImpaired
                )
            }.filter { it.fileId > 0 }
        } catch (e: Exception) {
            loggy("SubtitleSearch error: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    /** Downloads a subtitle file and saves it locally, returning the file path. */
    suspend fun download(fileId: Int): String? {
        return try {
            val response = client.get("$BASE_URL/download") {
                url { parameters.append("file_id", fileId.toString()) }
                header("Api-Key", API_KEY)
                header("User-Agent", "Syncplay-Mobile v1.0")
            }
            val downloadInfo = Json { ignoreUnknownKeys = true }.decodeFromString<OpenSubtitlesDownloadResponse>(response.bodyAsText())
            val subtitleUrl = downloadInfo.link

            /* Download actual subtitle file content */
            val subtitleContent = client.get(subtitleUrl).bodyAsText()

            /* Save to app's log/subtitle directory */
            val dir = getLogDirectoryPath() ?: return null
            val filename = downloadInfo.fileName
            val path = "$dir/$filename"
            app.utils.appendToFile(path, subtitleContent)
            path
        } catch (e: Exception) {
            loggy("SubtitleSearch download error: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}

data class SubtitleResult(
    val fileId: Int,
    val filename: String,
    val language: String,
    val releaseInfo: String,
    val downloadCount: Int,
    val hearingImpaired: Boolean
)

/* ===== API response models ===== */

@Serializable
data class OpenSubtitlesSearchResponse(
    val data: List<OpenSubtitlesItem> = emptyList()
)

@Serializable
data class OpenSubtitlesItem(
    val attributes: OpenSubtitlesAttributes
)

@Serializable
data class OpenSubtitlesAttributes(
    val language: String = "",
    val release: String = "",
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
    val files: List<OpenSubtitlesFile> = emptyList()
)

@Serializable
data class OpenSubtitlesFile(
    @SerialName("file_id") val fileId: Int = 0,
    @SerialName("file_name") val fileName: String = ""
)

@Serializable
data class OpenSubtitlesDownloadResponse(
    val link: String = "",
    @SerialName("file_name") val fileName: String = ""
)
