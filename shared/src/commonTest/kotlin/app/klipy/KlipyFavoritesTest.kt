package app.klipy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A favourite saved from a chat link must land in the right tab of the GIF panel, and its id must
 * never collide with a real Klipy id or with another saved link, because the panel's grid keys
 * its tiles by id and a duplicate key crashes the grid.
 */
class KlipyFavoritesTest {

    @Test
    fun webpLinkIsASticker() {
        assertEquals(KlipyMediaType.STICKER, KlipyFavorites.fromChatLink("https://static.klipy.com/a/b.webp").type)
        assertEquals(KlipyMediaType.STICKER, KlipyFavorites.fromChatLink("https://static.klipy.com/a/b.WEBP?x=1").type)
    }

    @Test
    fun gifLinkIsAGif() {
        assertEquals(KlipyMediaType.GIF, KlipyFavorites.fromChatLink("https://static.klipy.com/a/b.gif").type)
        // The query is not the path: a ".webp" there says nothing about the file.
        assertEquals(KlipyMediaType.GIF, KlipyFavorites.fromChatLink("https://static.klipy.com/a/b.gif?f=.webp").type)
    }

    @Test
    fun theLinkIsBothPreviewAndFullSize() {
        val link = "https://static.klipy.com/a/b.gif"
        val media = KlipyFavorites.fromChatLink(link)
        assertEquals(link, media.fullUrl)
        assertEquals(link, media.previewUrl)
        assertEquals("", media.slug)
    }

    @Test
    fun idIsNegativeAndStable() {
        val links = listOf("", "a", "https://static.klipy.com/a/b.gif", "https://x.example/" + "z".repeat(500))
        for (link in links) {
            val id = KlipyFavorites.fromChatLink(link).id
            assertTrue(id < 0, "id for '$link' is $id")
            assertEquals(id, KlipyFavorites.fromChatLink(link).id)
        }
    }

    @Test
    fun differentLinksGetDifferentIds() {
        val ids = (0 until 2000).map { KlipyFavorites.fromChatLink("https://static.klipy.com/g/$it.gif").id }
        assertEquals(ids.size, ids.toSet().size)
        assertNotEquals(
            KlipyFavorites.fromChatLink("https://static.klipy.com/a.gif").id,
            KlipyFavorites.fromChatLink("https://static.klipy.com/b.gif").id,
        )
    }
}
