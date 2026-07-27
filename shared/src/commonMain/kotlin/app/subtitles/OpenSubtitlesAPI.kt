package app.subtitles

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The OpenSubtitles **.com** REST API (the .org XML-RPC API is dead).
 *
 * Docs: https://opensubtitles.stoplight.io/docs/opensubtitles-api
 * Base URL: `https://api.opensubtitles.com/api/v1/`
 *
 * Every request must carry an `Api-Key` header and a non-generic `User-Agent` of the form
 * `AppName vX.Y.Z`; both are applied client-wide in [SubtitleSearch]'s client.
 */
interface OpenSubtitlesAPI {

    /**
     * Searches subtitles. [languages] must be lower-case, comma-separated and alphabetically
     * sorted; queries match case-insensitively server-side. Passing null omits the filter
     * entirely, so the API returns matches in every language.
     *
     * Parameters are declared in ALPHABETICAL order: the API 301-redirects any request whose
     * query string isn't in canonical (sorted) order, and Ktorfit emits parameters in declaration
     * order, so keeping them sorted avoids a redirect round-trip on every search.
     */
    @GET("subtitles")
    suspend fun search(
        @Query("languages") languages: String? = null,
        @Query("order_by") orderBy: String = "download_count",
        @Query("order_direction") orderDirection: String = "desc",
        @Query("page") page: Int = 1,
        @Query("query") query: String
    ): OpenSubtitlesSearchResponse

    /**
     * Requests a download link for a subtitle file. MUST be a POST with a JSON body
     * (`{"file_id": N}`); the API rejects a GET with a query parameter. The returned link is a
     * direct, UTF-8, ~3-hour-valid URL to the subtitle text.
     *
     * The explicit Content-Type header is LOAD-BEARING: Ktorfit's @Body only calls setBody(),
     * and Ktor's ContentNegotiation serializes an outbound body only when the request declares
     * a matching Content-Type. Without it the call dies client-side before any network I/O
     * ("Fail to prepare request body for sending... Content-Type: null").
     */
    @POST("download")
    @Headers("Content-Type: application/json")
    suspend fun requestDownload(@Body body: OpenSubtitlesDownloadRequest): OpenSubtitlesDownloadResponse
}

/* ===== Wire models (subset of the documented shapes; unknown keys are ignored) ===== */

@Serializable
data class OpenSubtitlesSearchResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
    val page: Int = 1,
    val data: List<OpenSubtitlesItem> = emptyList()
)

@Serializable
data class OpenSubtitlesItem(
    val id: String? = null,
    val type: String? = null,
    val attributes: OpenSubtitlesAttributes = OpenSubtitlesAttributes()
)

@Serializable
data class OpenSubtitlesAttributes(
    @SerialName("subtitle_id") val subtitleId: String? = null,
    val language: String = "",
    val release: String = "",
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
    @SerialName("from_trusted") val fromTrusted: Boolean = false,
    @SerialName("ai_translated") val aiTranslated: Boolean = false,
    @SerialName("machine_translated") val machineTranslated: Boolean = false,
    val fps: Double = 0.0,
    val files: List<OpenSubtitlesFile> = emptyList()
)

@Serializable
data class OpenSubtitlesFile(
    @SerialName("file_id") val fileId: Int = 0,
    @SerialName("cd_number") val cdNumber: Int = 1,
    @SerialName("file_name") val fileName: String = ""
)

@Serializable
data class OpenSubtitlesDownloadRequest(
    @SerialName("file_id") val fileId: Int
)

@Serializable
data class OpenSubtitlesDownloadResponse(
    val link: String = "",
    @SerialName("file_name") val fileName: String = "",
    /** Downloads consumed in the current window. */
    val requests: Int = 0,
    /** Downloads left in the current window — the API enforces a daily quota per key/IP. */
    val remaining: Int = 0,
    val message: String = "",
    @SerialName("reset_time") val resetTime: String = ""
)
