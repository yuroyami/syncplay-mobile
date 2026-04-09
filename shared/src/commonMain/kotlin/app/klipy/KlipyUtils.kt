package app.klipy

import SyncplayMobile.shared.BuildConfig
import app.klipy.KlipyUtils.trending
import app.preferences.Preferences.USER_ID
import app.preferences.value
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
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
    val httpClient = HttpClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true } ) } }
    private val ktorfit by lazy { Ktorfit.Builder().baseUrl(BASE_URL).httpClient(httpClient).build() }
    private val klipy by lazy { ktorfit.createKlipyAPI() }
    val customerId by lazy { USER_ID.value() ?: Uuid.generateV4().toHexString() }

    /**
     * Searches Klipy for GIFs or stickers.
     *
     * If the query is blank, returns [trending] results instead.
     */
    suspend fun search(
        query: String,
        type: KlipyMediaType = KlipyMediaType.GIF,
        limit: Int = 24
    ): List<KlipyMedia> {
        if (query.isBlank()) return trending(type, limit)

        return try {
            val result = when (type) {
                KlipyMediaType.GIF -> klipy.searchGifs(query = query, perPage = limit, customerId = customerId)
                KlipyMediaType.STICKER -> klipy.searchStickers(query = query, perPage = limit, customerId = customerId)
            }
            result.toMediaList(type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetches trending GIFs or stickers.
     */
    suspend fun trending(
        type: KlipyMediaType = KlipyMediaType.GIF,
        limit: Int = 24
    ): List<KlipyMedia> {
        return try {
            val result = when (type) {
                KlipyMediaType.GIF -> klipy.trendingGifs(perPage = limit, customerId = customerId)
                KlipyMediaType.STICKER -> klipy.trendingStickers(perPage = limit, customerId = customerId)
            }
            result.toMediaList(type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetches recently used/shared GIFs or stickers for this user.
     */
    suspend fun recents(
        type: KlipyMediaType = KlipyMediaType.GIF,
        limit: Int = 24
    ): List<KlipyMedia> {
        return try {
            val result = when (type) {
                KlipyMediaType.GIF -> klipy.recentGifs(customerId = customerId, perPage = limit)
                KlipyMediaType.STICKER -> klipy.recentStickers(customerId = customerId, perPage = limit)
            }
            result.toMediaList(type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Fires a share event so the item appears in the user's recents. */
    suspend fun trackShare(slug: String, type: KlipyMediaType) {
        try {
            when (type) {
                KlipyMediaType.GIF -> klipy.shareGif(slug)
                KlipyMediaType.STICKER -> klipy.shareSticker(slug)
            }
        } catch (_: Exception) { /* best-effort analytics */ }
    }

    /** Maps an API response to a flat [KlipyMedia] list. */
    private fun GifSearchResponse.toMediaList(type: KlipyMediaType): List<KlipyMedia> {
        return data.data.map { item ->
            val isSticker = type == KlipyMediaType.STICKER
            KlipyMedia(
                id = item.id,
                slug = item.slug,
                previewUrl = if (isSticker) item.file.sm.webp.url else item.file.sm.gif.url,
                fullUrl = if (isSticker) item.file.hd.webp.url else item.file.hd.gif.url,
                type = type
            )
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
