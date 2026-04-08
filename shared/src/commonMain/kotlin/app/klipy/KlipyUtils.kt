package app.klipy

import SyncplayMobile.shared.BuildConfig
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client for the Klipy GIF/Sticker API.
 *
 * Supports searching for GIFs and stickers and returns preview/full-size URLs.
 */
object KlipyUtils {
    private val BASE_URL = "https://api.klipy.com/v1/${BuildConfig.KLIPY_API_KEY}/"

    private val ktorfit by lazy { Ktorfit.Builder().baseUrl(BASE_URL).build() }
    private val klipy by lazy { ktorfit.createKlipyAPI() }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    /**
     * Searches Klipy for GIFs or stickers.
     *
     * @param query The search query string
     * @param type Either [KlipyMediaType.GIF] or [KlipyMediaType.STICKER]
     * @param limit Maximum number of results to return
     * @return A list of [KlipyMedia] results, or empty on failure
     */
    suspend fun search(
        query: String,
        type: KlipyMediaType = KlipyMediaType.GIF,
        limit: Int = 30
    ): List<KlipyMedia> {
        if (query.isBlank()) return trending(type, limit)

        return try {
            val endpoint = when (type) {
                KlipyMediaType.GIF -> "$BASE_URL/gifs/search"
                KlipyMediaType.STICKER -> "$BASE_URL/stickers/search"
            }

            val response = client.get(endpoint) {
                parameter("q", query)
                parameter("limit", limit)
            }

            parseResponse(response.bodyAsText())
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
        limit: Int = 30
    ): List<KlipyMedia> {
        return try {
            val endpoint = when (type) {
                KlipyMediaType.GIF -> "$BASE_URL/gifs/trending"
                KlipyMediaType.STICKER -> "$BASE_URL/stickers/trending"
            }

            val response = client.get(endpoint) {
                parameter("limit", limit)
            }

            parseResponse(response.bodyAsText())
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseResponse(body: String): List<KlipyMedia> {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val results = root["results"]?.jsonArray ?: root["data"]?.jsonArray ?: return emptyList()

            results.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null

                // Try to extract media URLs from various response formats
                val media = obj["media"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: obj["images"]?.jsonObject

                val previewUrl = extractUrl(media, "tinygif", "preview", "fixed_width_small")
                    ?: extractUrl(obj, "preview_url", "thumbnail")
                    ?: return@mapNotNull null

                val fullUrl = extractUrl(media, "gif", "original", "fixed_width")
                    ?: extractUrl(obj, "url", "gif_url")
                    ?: previewUrl

                KlipyMedia(
                    id = id,
                    previewUrl = previewUrl,
                    fullUrl = fullUrl
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Tries to extract a URL from nested JSON structures with various key patterns */
    private fun extractUrl(obj: JsonObject?, vararg keys: String): String? {
        if (obj == null) return null
        for (key in keys) {
            // Direct string field
            obj[key]?.jsonPrimitive?.content?.let { return it }
            // Nested object with "url" field
            obj[key]?.jsonObject?.get("url")?.jsonPrimitive?.content?.let { return it }
        }
        return null
    }
}

@Serializable
data class KlipyMedia(
    val id: String,
    val previewUrl: String,
    val fullUrl: String
)

enum class KlipyMediaType {
    GIF, STICKER
}
