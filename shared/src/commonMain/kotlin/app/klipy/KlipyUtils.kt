package app.klipy

import SyncplayMobile.shared.BuildConfig
import app.klipy.KlipyUtils.trending
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
 * Client for the Klipy GIF/Sticker API.
 *
 * Supports searching, trending, and recent items for both GIFs and stickers.
 */
object KlipyUtils {
    private val BASE_URL = "https://api.klipy.com/api/v1/${BuildConfig.KLIPY_API_KEY}/"
    private val klipyHttpClient by lazy { httpClient.config(additionalHttpConfig) }
    private val ktorfit by lazy { Ktorfit.Builder().baseUrl(BASE_URL).httpClient(klipyHttpClient).build() }
    private val klipy by lazy { ktorfit.createKlipyAPI() }
    private val customerId by lazy { USER_ID.value() ?: Uuid.generateV4().toHexString() }

    /**
     * Searches Klipy for GIFs or stickers.
     *
     * If the query is blank, returns [trending] results instead.
     */
    suspend fun search(
        query: String,
        type: KlipyMediaType = KlipyMediaType.GIF,
        limit: Int = 24,
        page: Int = 1
    ): KlipyPagedResult {
        if (query.isBlank()) return trending(type, limit, page)

        loggy("KlipyUtils.search → type=$type query='$query' limit=$limit page=$page customerId=$customerId baseUrl=$BASE_URL")
        return try {
            val result = when (type) {
                KlipyMediaType.GIF -> klipy.searchGifs(query = query, perPage = limit, customerId = customerId, page = page)
                KlipyMediaType.STICKER -> klipy.searchStickers(query = query, perPage = limit, customerId = customerId, page = page)
            }
            loggy("KlipyUtils.search ← OK ${result.data.data.size} items, hasNext=${result.data.hasNext}")
            result.toPagedResult(type)
        } catch (e: CancellationException) {
            // Compose forgot the LaunchedEffect (panel closed / re-rendered) — propagate
            // so the coroutine actually cancels instead of returning an empty result that
            // would render "No results" briefly before unmount.
            loggy("KlipyUtils.search ← cancelled (panel closed / recompose)")
            throw e
        } catch (e: Exception) {
            loggy("KlipyUtils.search ← FAIL ${e::class.simpleName}: ${e.message}")
            loggy(e)
            KlipyPagedResult(emptyList(), false)
        }
    }

    /**
     * Fetches trending GIFs or stickers.
     */
    suspend fun trending(
        type: KlipyMediaType = KlipyMediaType.GIF,
        limit: Int = 24,
        page: Int = 1
    ): KlipyPagedResult {
        loggy("KlipyUtils.trending → type=$type limit=$limit page=$page customerId=$customerId baseUrl=$BASE_URL")
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
            KlipyPagedResult(emptyList(), false)
        }
    }

    /**
     * Fetches recently used/shared GIFs or stickers for this user.
     */
    suspend fun recents(
        type: KlipyMediaType = KlipyMediaType.GIF,
        limit: Int = 24,
        page: Int = 1
    ): KlipyPagedResult {
        loggy("KlipyUtils.recents → type=$type limit=$limit page=$page customerId=$customerId baseUrl=$BASE_URL")
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
            KlipyPagedResult(emptyList(), false)
        }
    }

    /** Fires a share event so the item appears in the user's recents. */
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

    /** Maps an API response to a [KlipyPagedResult]. */
    private fun KlipySearchResponse.toPagedResult(type: KlipyMediaType): KlipyPagedResult {
        val items = data.data.map { item ->
            val isSticker = type == KlipyMediaType.STICKER
            KlipyMedia(
                id = item.id,
                slug = item.slug,
                previewUrl = if (isSticker) item.file.sm.webp.url else item.file.sm.gif.url,
                fullUrl = if (isSticker) item.file.hd.webp.url else item.file.hd.gif.url,
                type = type
            )
        }
        return KlipyPagedResult(items = items, hasNext = data.hasNext)
    }

    private val additionalHttpConfig: (HttpClientConfig<*>.() -> Unit)
        get() = {
            /* expectSuccess=true makes 4xx/5xx responses throw ResponseException instead of
             * being silently parsed as successful empties (Ktor 3 default is false). Without
             * this, a Cloudflare 403 / OpenSubtitles 401 on iOS would deserialize through the
             * lenient JSON path below into KlipySearchResponse(data=KlipySearchWrapper()) — no
             * exception, no log, just an empty grid. The catch blocks above will now actually
             * fire and surface the real status code. */
            expectSuccess = true

            install(ContentNegotiation) {
                json(
                    /* ignoreUnknownKeys keeps Klipy's response-shape drift from breaking us.
                     * coerceInputValues=true is intentionally NOT set: combined with default
                     * field values on the DTOs, it would silently absorb wrong-shape error
                     * bodies as empty successes, which is exactly the failure we just fixed
                     * with expectSuccess. If a 200 OK ever returns a malformed schema, we
                     * want it to throw so we can see it. */
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                )
            }
            install(HttpTimeout) {
                /* Override the base 15s/10s/15s with a slightly more generous envelope —
                 * search requests can take longer on slow mobile networks. */
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
            /* Accept-only override. User-Agent is already set by the base httpClient's
             * defaultRequest; setting it again here would result in two values that Ktor's
             * mergeHeaders joins with a comma ("App/0.19.1,App/0.19.1") — a malformed UA
             * that Cloudflare's bot heuristics flag as suspicious on Apple Secure Transport
             * traffic in particular. */
            defaultRequest {
                header(HttpHeaders.Accept, "application/json")
            }
        }
}

@Serializable
data class KlipyMedia(
    val id: Long,
    val slug: String = "",
    val previewUrl: String,
    val fullUrl: String,
    val type: KlipyMediaType = KlipyMediaType.GIF
)

enum class KlipyMediaType {
    GIF, STICKER
}

data class KlipyPagedResult(
    val items: List<KlipyMedia>,
    val hasNext: Boolean
)
