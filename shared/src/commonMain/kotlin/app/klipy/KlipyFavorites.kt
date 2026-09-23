package app.klipy

import app.preferences.Preferences.KLIPY_FAVORITES
import app.preferences.set
import app.preferences.value
import app.utils.urlPath
import kotlinx.serialization.json.Json

/**
 * The favourite GIFs and stickers from Klipy (the GIF service of the chat's GIF panel). Each
 * entry is one JSON [KlipyMedia] in [KLIPY_FAVORITES].
 *
 * The full-size link identifies an entry. The GIF panel knows an item's Klipy id, but a chat
 * message carries only the link, so the link is the one thing that both places can compare.
 */
object KlipyFavorites {

    fun load(): List<KlipyMedia> = KLIPY_FAVORITES.value().mapNotNull { json ->
        runCatching { Json.decodeFromString<KlipyMedia>(json) }.getOrNull()
    }

    fun links(): Set<String> = load().mapTo(HashSet()) { it.fullUrl }

    suspend fun add(media: KlipyMedia) {
        if (media.fullUrl in links()) return
        KLIPY_FAVORITES.set(KLIPY_FAVORITES.value() + Json.encodeToString(media))
    }

    suspend fun remove(link: String) {
        val kept = KLIPY_FAVORITES.value().filter { json ->
            runCatching { Json.decodeFromString<KlipyMedia>(json).fullUrl != link }.getOrDefault(true)
        }.toSet()
        KLIPY_FAVORITES.set(kept)
    }

    /**
     * A favourite made from a chat link, which has no Klipy id, slug or small preview. The id is
     * negative, so it never matches a real Klipy id in the panel's grid keys. A `.webp` link is a
     * sticker, because the panel sends stickers as WebP and GIFs as GIF.
     */
    fun fromChatLink(link: String): KlipyMedia = KlipyMedia(
        id = -(linkHash(link) and Long.MAX_VALUE) - 1,
        previewUrl = link,
        fullUrl = link,
        type = if (urlPath(link).endsWith(".webp", ignoreCase = true)) KlipyMediaType.STICKER else KlipyMediaType.GIF,
    )

    /** A 64-bit FNV-1a hash, so the id is the same on every platform and every launch. */
    private fun linkHash(link: String): Long {
        var hash = -0x340d631b7bdddcdbL
        for (char in link) hash = (hash xor char.code.toLong()) * 0x100000001b3L
        return hash
    }
}
