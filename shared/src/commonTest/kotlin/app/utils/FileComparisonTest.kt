package app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the file identity helpers of [FileComparison] to vectors computed with the Python
 * reference functions in `utils.py`: stripfilename, hashFilename, hashFilesize, sameHashed,
 * sameFilename, sameFilesize and sameFileduration.
 *
 * The hash vectors are sha256 hex digests of the stripped names, cut to 12 characters. If a
 * vector drifts, the app stops matching files with PC clients that send hashed names (privacy mode).
 */
class FileComparisonTest {

    // sha256("MovieName2024mkv")[:12]: the separators ([-~_.\[\](): ]) are stripped before hashing.
    @Test
    fun `hashFilename strips separators like python before hashing`() {
        assertEquals("755b2c97807b", FileComparison.hashFilename("Movie.Name.2024.mkv"))
    }

    // sha256("MovieNamemkv")[:12]: spaces are separators too.
    @Test
    fun `hashFilename strips spaces`() {
        assertEquals("a9858cb4803c", FileComparison.hashFilename("Movie Name.mkv"))
    }

    // sha256("MyVideomp4")[:12]: a URL uses its last path segment, percent-decoded, then stripped.
    @Test
    fun `hashFilename of URL uses decoded last path segment`() {
        assertEquals("30e346a16db8", FileComparison.hashFilename("https://example.com/path/My%20Video.mp4"))
    }

    // sha256("123456789")[:12]
    @Test
    fun `hashFilesize hashes the decimal string`() {
        assertEquals("15e2b0d3c338", FileComparison.hashFilesize("123456789"))
    }

    @Test
    fun `percentDecode decodes UTF-8 escapes and leaves malformed ones alone`() {
        assertEquals("My Video", FileComparison.percentDecode("My%20Video"))
        assertEquals("café", FileComparison.percentDecode("caf%C3%A9"))
        assertEquals("100%", FileComparison.percentDecode("100%"))
        assertEquals("a%zzb", FileComparison.percentDecode("a%zzb"))
    }

    @Test
    fun `sameFilename matches raw against hashed from a privacy-mode peer`() {
        val raw = "Movie.Name.2024.mkv"
        val theirHash = FileComparison.hashFilename(raw) // what a PC peer in privacy mode sends
        assertTrue(FileComparison.sameFilename(raw, theirHash))
        assertTrue(FileComparison.sameFilename(theirHash, raw))
        assertTrue(FileComparison.sameFilename(theirHash, theirHash))
    }

    @Test
    fun `sameFilename is case-insensitive on raw names`() {
        assertTrue(FileComparison.sameFilename("MOVIE.MKV", "movie.mkv"))
    }

    @Test
    fun `sameFilename treats the hidden sentinel as matching anything`() {
        assertTrue(FileComparison.sameFilename(FileComparison.PRIVACY_HIDDENFILENAME, "whatever.mkv"))
        assertTrue(FileComparison.sameFilename("whatever.mkv", FileComparison.PRIVACY_HIDDENFILENAME))
    }

    @Test
    fun `sameFilename matches a URL against its local filename`() {
        // PC client: stripURL = isURL(f1) XOR isURL(f2), so the URL side becomes its last segment.
        assertTrue(FileComparison.sameFilename("https://example.com/path/My%20Video.mp4", "My Video.mp4"))
    }

    @Test
    fun `sameFilename rejects genuinely different names`() {
        assertFalse(FileComparison.sameFilename("Episode.01.mkv", "Episode.02.mkv"))
    }

    @Test
    fun `sameFilesize passes unknown sizes and matches hashed against raw`() {
        assertTrue(FileComparison.sameFilesize("0", "123456789"))
        assertTrue(FileComparison.sameFilesize("", "123456789"))
        assertTrue(FileComparison.sameFilesize("123456789", "123456789"))
        assertTrue(FileComparison.sameFilesize("123456789", FileComparison.hashFilesize("123456789")))
        assertFalse(FileComparison.sameFilesize("123456789", "987654321"))
    }

    @Test
    fun `sameFileduration tolerates rounding differences under two and a half seconds`() {
        assertTrue(FileComparison.sameFileduration(7200.0, 7201.4))
        assertTrue(FileComparison.sameFileduration(7200.6, 7200.0))
        assertFalse(FileComparison.sameFileduration(7200.0, 7203.0))
    }

    @Test
    fun `playlistIsValid enforces python's caps`() {
        assertTrue(playlistIsValid(List(250) { "a" }))
        assertFalse(playlistIsValid(List(251) { "a" }))
        assertFalse(playlistIsValid(listOf("x".repeat(10001))))
        assertTrue(playlistIsValid(listOf("x".repeat(10000))))
    }
}
