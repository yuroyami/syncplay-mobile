package app.room

import app.room.sharedplaylist.SharedPlaylistManager
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the trusted-domain entry matcher against PC's `_isURITrustableAndTrusted` rules
 * (client.py:565-602): exact host, `www.` variant, one-label `*` wildcards, and optional
 * path-prefix constraints. Notably, arbitrary subdomains are NOT trusted without an
 * explicit wildcard — the old `endsWith` matcher trusted every subdomain.
 */
class TrustedDomainsTest {

    private fun matches(entry: String, domain: String, path: String = "") =
        SharedPlaylistManager.trustedEntryMatches(entry, domain, path)

    @Test
    fun `exact host and www variant match`() {
        assertTrue(matches("example.com", "example.com"))
        assertTrue(matches("example.com", "www.example.com"))
    }

    @Test
    fun `arbitrary subdomains do not match without a wildcard`() {
        assertFalse(matches("example.com", "cdn.example.com"))
        assertFalse(matches("example.com", "evil-example.com"))
        assertFalse(matches("example.com", "example.com.evil.net"))
    }

    @Test
    fun `wildcard matches exactly one label`() {
        assertTrue(matches("*.example.com", "cdn.example.com"))
        assertFalse(matches("*.example.com", "a.b.example.com"))
        assertFalse(matches("*.example.com", "example.com"))
    }

    @Test
    fun `path prefix entries constrain the URL path`() {
        assertTrue(matches("example.com/videos", "example.com", "/videos/movie.mp4"))
        assertFalse(matches("example.com/videos", "example.com", "/other/movie.mp4"))
        assertFalse(matches("example.com/videos", "example.com", ""))
    }
}
