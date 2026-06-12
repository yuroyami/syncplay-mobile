package app.utils

import kotlin.math.roundToLong

/**
 * Cross-client file identity helpers, ported 1:1 from the reference python client's
 * `utils.py` (stripfilename / hashFilename / hashFilesize / sameHashed / sameFilename /
 * sameFilesize / sameFileduration).
 *
 * These MUST stay byte-identical to the python implementations: the whole point of the
 * filename/filesize privacy modes is that a hashed value computed on one client equals
 * the hashed value computed independently on another client for the same file. Any
 * difference (a missed strip character, a skipped URL-unquote) makes every privacy-mode
 * user appear to be playing a different file on every other client.
 */
object FileComparison {

    /** Sent in place of the filename when the user picked the "don't send" privacy mode.
     * Python's `constants.PRIVACY_HIDDENFILENAME` — peers treat it as matching anything. */
    const val PRIVACY_HIDDENFILENAME = "**Hidden filename**"

    /** Python's `constants.FILENAME_STRIP_REGEX = r"[-~_\.\[\](): ]"`. */
    private val FILENAME_STRIP_REGEX = Regex("[-~_.\\[\\](): ]")

    /** Python's `utils.isURL` — the protocol-level "is this entry a URL" test. */
    fun isWireURL(path: String?): Boolean = path?.contains("://") == true

    /**
     * Python's `urllib.parse.unquote`: decodes `%XX` percent-escapes as UTF-8, leaving
     * malformed escapes untouched. No `+`-to-space handling (that's `unquote_plus`).
     */
    fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val out = StringBuilder(s.length)
        val bytes = ArrayList<Byte>(8)
        var i = 0
        fun flushBytes() {
            if (bytes.isNotEmpty()) {
                out.append(bytes.toByteArray().decodeToString())
                bytes.clear()
            }
        }
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3)
                val b = hex.toIntOrNull(16)
                if (b != null) {
                    bytes.add(b.toByte())
                    i += 3
                    continue
                }
            }
            flushBytes()
            out.append(c)
            i++
        }
        flushBytes()
        return out.toString()
    }

    /**
     * Python's `utils.stripfilename(filename, stripURL)`: percent-decode, optionally keep
     * only the last path segment of a URL (decoded again, mirroring python's double
     * unquote), then drop every separator/decoration character so that
     * "Movie.Name.2024.mkv" and "Movie Name 2024.mkv" normalize identically.
     */
    fun stripFilename(filename: String?, stripURL: Boolean): String {
        if (filename.isNullOrEmpty()) return ""
        var f = percentDecode(filename)
        if (stripURL) f = percentDecode(f.substringAfterLast("/"))
        return FILENAME_STRIP_REGEX.replace(f, "")
    }

    /** Python's `utils.hashFilename`: sha256 of the stripped name, first 12 hex chars. */
    fun hashFilename(filename: String?, stripURL: Boolean = false): String {
        val strip = stripURL || isWireURL(filename)
        return sha256(stripFilename(filename, strip)).toHexString(HexFormat.Default).take(12)
    }

    /** Python's `utils.hashFilesize`: sha256 of the decimal string, first 12 hex chars. */
    fun hashFilesize(size: String): String =
        sha256(size).toHexString(HexFormat.Default).take(12)

    /**
     * Python's `utils.sameHashed`: two values match when their raw forms are equal
     * (case-insensitively first), or when either side's raw form equals the other
     * side's hashed form, or when both hashes are equal. This is what lets a client
     * sending raw names agree with a client sending hashed names.
     */
    fun sameHashed(raw1: String, hashed1: String, raw2: String, hashed2: String): Boolean =
        raw1.lowercase() == raw2.lowercase() ||
            raw1 == raw2 ||
            raw1 == hashed2 ||
            hashed1 == raw2 ||
            hashed1 == hashed2

    /** Python's `utils.sameFilename`. The hidden-filename sentinel matches anything. */
    fun sameFilename(filename1: String?, filename2: String?): Boolean {
        val f1 = filename1 ?: ""
        val f2 = filename2 ?: ""
        if (f1 == PRIVACY_HIDDENFILENAME || f2 == PRIVACY_HIDDENFILENAME) return true
        val stripURL = isWireURL(f1) xor isWireURL(f2)
        return sameHashed(
            stripFilename(f1, stripURL), hashFilename(f1, stripURL),
            stripFilename(f2, stripURL), hashFilename(f2, stripURL)
        )
    }

    /**
     * Python's `utils.sameFilesize`. Size 0 means "unknown / withheld" and matches
     * anything; sizes arrive as strings on our side (the wire serializer normalizes
     * number-vs-string), python's int-vs-str difference disappears inside the hash.
     */
    fun sameFilesize(filesize1: String?, filesize2: String?): Boolean {
        val s1 = filesize1 ?: ""
        val s2 = filesize2 ?: ""
        if (s1.isEmpty() || s2.isEmpty() || s1 == "0" || s2 == "0") return true
        return sameHashed(s1, hashFilesize(s1), s2, hashFilesize(s2))
    }

    /** Python's `utils.sameFileduration`: rounded difference under 2.5 s is the same file. */
    fun sameFileduration(duration1: Double, duration2: Double): Boolean =
        kotlin.math.abs(duration1.roundToLong() - duration2.roundToLong()) < DIFFERENT_DURATION_THRESHOLD

    /** Python's `constants.DIFFERENT_DURATION_THRESHOLD`. */
    private const val DIFFERENT_DURATION_THRESHOLD = 2.5
}
