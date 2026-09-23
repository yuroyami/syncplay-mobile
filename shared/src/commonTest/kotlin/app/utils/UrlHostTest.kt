package app.utils

import app.room.models.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Every trust decision in the app starts with the host that a URL names, and the URL comes from
 * whoever is in the room. These are the URL shapes that a naive parser reads wrongly.
 */
class UrlHostTest {

    @Test
    fun `user info does not become the host`() {
        assertEquals("unapproved.example", urlHost("https://trusted.example:pw@unapproved.example/movie.mp4"))
    }

    @Test
    fun `ports and case are dropped`() {
        assertEquals("cdn.example.com", urlHost("HTTPS://CDN.Example.com:8443/a.mp4"))
    }

    @Test
    fun `ipv6 literals keep their address`() {
        assertEquals("2001:db8::1", urlHost("http://[2001:db8::1]:8080/x"))
    }

    @Test
    fun `query and fragment do not hide the path split`() {
        assertEquals("h.example", urlHost("https://h.example?x=1"))
        assertEquals("/videos/x.mp4", urlPath("https://h.example/videos/x.mp4?t=1#f"))
    }

    @Test
    fun `a host with no path has no path`() {
        assertEquals("", urlPath("https://h.example"))
        assertEquals("", urlPath("https://h.example?x=1"))
    }

    @Test
    fun `no scheme or empty authority means no host`() {
        assertNull(urlHost("movie.mkv"))
        assertNull(urlHost("https:///x"))
    }

    @Test
    fun `a spoofed chat image host is not trusted`() {
        val m = Message(sender = "peer", content = "https://klipy.com:x@evil.example/a.gif")
        assertFalse(m.isFromTrustedImageHost)
        assertEquals("evil.example", m.imageHost)
    }

    @Test
    fun `the real gif host still loads without asking`() {
        assertEquals(true, Message(sender = "peer", content = "https://cdn.klipy.com/a.gif").isFromTrustedImageHost)
    }
}
