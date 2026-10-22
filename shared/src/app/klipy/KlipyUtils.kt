package app.klipy

import SyncplayMobile.shared.KiteBuildConfig
import app.klipy.KlipyUtils.trending
import app.preferences.Preferences.GIF_REMEMBER_RECENTS
import app.preferences.Preferences.USER_ID
import app.preferences.value
import app.utils.httpClient
import app.utils.loggy
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * Client for the Klipy GIF and sticker API. It searches and lists the trending and recent
 * items, for both GIFs and stickers.
 */
object KlipyUtils {
    private val BASE_URL = "https://api.klipy.com/api/v1/${KiteBuildConfig.KLIPY_API_KEY}/"
    private val klipyHttpClient by lazy { httpClient.config(additionalHttpConfig) }
    private val ktorfit by lazy { Ktorfit.Builder().baseUrl(BASE_URL).httpClient(klipyHttpClient).build() }
    private val klipy by lazy { ktorfit.createKlipyAPI() }
    /** A new id for each launch, which [customerId] falls back to. */
    private val sessionId by lazy { Uuid.generateV4().toHexString() }

    /**
     * The id that Klipy sees. It is the app's persisted [USER_ID] only while "Remember recent
     * GIFs" is on. When that setting is off, Klipy gets a per-launch id instead. Klipy then
     * cannot link two launches, and the recent list comes back empty.
     */
    private val customerId: String
        get() = if (GIF_REMEMBER_RECENTS.value()) USER_ID.value() ?: sessionId else sessionId

    /** Searches Klipy for GIFs or stickers. A blank query returns the [trending] results. */
    suspend fun search(
        query: String,
        type: KlipyMediaType = KlipyMediaType.GIF,
        limit: Int = 24,
        page: Int = 1
    ): KlipyPagedResult {
        if (query.isBlank()) return trending(type, limit, page)

        loggy("KlipyUtils.search → type=$type limit=$limit page=$page")
        return try {
            val result = when (type) {
                KlipyMediaType.GIF -> klipy.searchGifs(query = query, perPage = limit, customerId = customerId, page = page)
                KlipyMediaType.STICKER -> klipy.searchStickers(query = query, perPage = limit, customerId = customerId, page = page)
            }
            loggy("KlipyUtils.search ← OK ${result.data.data.size} items, hasNext=${result.data.hasNext}")
            result.toPagedResult(type)
        } catch (e: CancellationException) {
            // The LaunchedEffect left the composition (the panel closed or recomposed).
            // Rethrow so that the coroutine cancels. An empty result would show "No results"
            // for a moment before the panel goes away.
            loggy("KlipyUtils.search ← cancelled (panel closed / recompose)")
            throw e
        } catch (e: Exception) {
            loggy("KlipyUtils.search ← FAIL ${e::class.simpleName}: ${e.message}")
            loggy(e)
            KlipyPagedResult(emptyList(), false, failed = true)
        }
    }

    /** Fetches the trending GIFs or stickers. */
    suspend fun trending(
        type: KlipyMediaType = KlipyMediaType.GIF,
        limit: Int = 24,
        page: Int = 1
    ): KlipyPagedResult {
        loggy("KlipyUtils.trending → type=$type limit=$limit page=$page")
        return try {
            val result = when (type) {
                KlipyMediaType.GIF -> klipy.trendingGifs(perPage = limit, customerId = customerId, page = page)
                KlipyMediaType.STICKER -> klipy.trendingStickers(perPage = limit, customerId = customerId, page = page)
            }
            loggy("KlipyUtils.trending ← OK ${result.data.data.size} items, hasNext=${result.data.hasNext}")
            result.toPagedResult(type)
        } catch (e: CancellationException) {
            loggy("KlipyUtils.trending ← cancelled")
            throw e
        } catch (e: Exception) {
            loggy("KlipyUtils.trending ← FAIL ${e::class.simpleName}: ${e.message}")
            loggy(e)
            KlipyPagedResult(emptyList(), false, failed = true)
        }
    }

    /** Fetches the GIFs or stickers that this user shared recently (see [trackShare]). */
    suspend fun recents(
        type: KlipyMediaType = KlipyMediaType.GIF,
        limit: Int = 24,
        page: Int = 1
    ): KlipyPagedResult {
        loggy("KlipyUtils.recents → type=$type limit=$limit page=$page")
        return try {
            val result = when (type) {
                KlipyMediaType.GIF -> klipy.recentGifs(customerId = customerId, perPage = limit, page = page)
                KlipyMediaType.STICKER -> klipy.recentStickers(customerId = customerId, perPage = limit, page = page)
            }
            loggy("KlipyUtils.recents ← OK ${result.data.data.size} items, hasNext=${result.data.hasNext}")
            result.toPagedResult(type)
        } catch (e: CancellationException) {
            loggy("KlipyUtils.recents ← cancelled")
            throw e
        } catch (e: Exception) {
            loggy("KlipyUtils.recents ← FAIL ${e::class.simpleName}: ${e.message}")
            loggy(e)
            KlipyPagedResult(emptyList(), false, failed = true)
        }
    }

    /** Sends a share event, so that the item appears in the user's recent list. */
    suspend fun trackShare(slug: String, type: KlipyMediaType) {
        try {
            when (type) {
                KlipyMediaType.GIF -> klipy.shareGif(slug, customerId)
                KlipyMediaType.STICKER -> klipy.shareSticker(slug, customerId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { /* best-effort analytics */ }
    }

    /** Maps an API response to a [KlipyPagedResult]: WebP files for stickers, GIF files for GIFs. */
    private fun KlipySearchResponse.toPagedResult(type: KlipyMediaType): KlipyPagedResult {
        val items = data.data.map { item ->
            val isSticker = type == KlipyMediaType.STICKER
            KlipyMedia(
                id = item.id,
                slug = item.slug,
                title = item.title,
                previewUrl = if (isSticker) item.file.sm.webp.url else item.file.sm.gif.url,
                fullUrl = if (isSticker) item.file.hd.webp.url else item.file.hd.gif.url,
                type = type
            )
        }
        return KlipyPagedResult(items = items, hasNext = data.hasNext)
    }

    private val additionalHttpConfig: (HttpClientConfig<*>.() -> Unit)
        get() = {
            /* expectSuccess makes a 4xx or 5xx response throw ResponseException (the Ktor 3
             * default is false). Without it, an error body such as a Cloudflare 403 parses
             * through the lenient JSON below into an empty KlipySearchResponse: no exception,
             * no log, only an empty grid. With it, the catch blocks above log the real status. */
            expectSuccess = true

            install(ContentNegotiation) {
                json(
                    /* ignoreUnknownKeys keeps new fields in Klipy's responses from breaking the
                     * parse. coerceInputValues stays off on purpose. Together with the default
                     * values on the DTOs, it would turn a wrong-shape body into an empty
                     * success, the same silent failure that expectSuccess prevents. A 200 OK
                     * with a malformed body must throw, so that the log shows it. */
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                )
            }
            install(HttpTimeout) {
                /* Raise the base timeouts (15 s request, 10 s connect, 15 s socket) a little,
                 * because a search can take longer on a slow mobile network. */
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
            /* Set only Accept here. The base httpClient's defaultRequest already sets
             * User-Agent. A second User-Agent makes Ktor's mergeHeaders join both values with
             * a comma ("App/0.19.1,App/0.19.1"). Cloudflare's bot checks flag that malformed
             * value, mostly on traffic from Apple's Secure Transport. */
            defaultRequest {
                header(HttpHeaders.Accept, "application/json")
            }
        }
}

@Serializable
data class KlipyMedia(
    val id: Long,
    val slug: String = "",
    /** The provider's caption, which the screen reader reads for the tile. */
    val title: String = "",
    val previewUrl: String,
    val fullUrl: String,
    val type: KlipyMediaType = KlipyMediaType.GIF
)

enum class KlipyMediaType {
    GIF, STICKER
}

data class KlipyPagedResult(
    val items: List<KlipyMedia>,
    val hasNext: Boolean,
    /** True when the request itself failed. The GIF panel then offers a retry, not "No results". */
    val failed: Boolean = false,
)
