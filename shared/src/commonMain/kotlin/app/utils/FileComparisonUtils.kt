package app.utils

import kotlin.math.roundToLong

/**
 * File identity helpers that every client must compute the same way, ported one to one from the
 * reference Python client's `utils.py` (stripfilename, hashFilename, hashFilesize, sameHashed,
 * sameFilename, sameFilesize and sameFileduration).
 *
 * These helpers must stay byte-identical to the Python functions. In the filename and filesize
 * privacy modes, the hash from one client must equal the hash that another client computes for
 * the same file. One difference (a missed strip character, a skipped URL unquote) makes every
 * privacy-mode user appear to play a different file on every other client.
 */
object FileComparison {

    /**
     * Sent in place of the filename when the user picks the "don't send" privacy mode. Python's
     * `constants.PRIVACY_HIDDENFILENAME`. Peers treat it as matching any filename.
     */
    const val PRIVACY_HIDDENFILENAME = "**Hidden filename**"

    /** Python's `constants.FILENAME_STRIP_REGEX = r"[-~_\.\[\](): ]"`. */
    private val FILENAME_STRIP_REGEX = Regex("[-~_.\\[\\](): ]")

    /** Python's `utils.isURL`: the test that the protocol uses to decide that an entry is a URL. */
    fun isWireURL(path: String?): Boolean = path?.contains("://") == true

    /**
     * Python's `urllib.parse.unquote`: decodes `%XX` percent escapes as UTF-8 and leaves a
     * malformed escape as it is. A `+` stays a `+` (only `unquote_plus` turns it into a space).
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
     * Python's `utils.stripfilename(filename, stripURL)`. It percent-decodes the name. With
     * [stripURL], it keeps only the last path segment and decodes that segment again, like
     * Python's double unquote. Then it drops every separator and decoration character, so
     * "Movie.Name.2024.mkv" and "Movie Name 2024.mkv" give the same result.
     */
    fun stripFilename(filename: String?, stripURL: Boolean): String {
        if (filename.isNullOrEmpty()) return ""
        var f = percentDecode(filename)
        if (stripURL) f = percentDecode(f.substringAfterLast("/"))
        return FILENAME_STRIP_REGEX.replace(f, "")
    }

    /** Python's `utils.hashFilename`: SHA-256 of the stripped name, first 12 hex characters. */
    fun hashFilename(filename: String?, stripURL: Boolean = false): String {
        val strip = stripURL || isWireURL(filename)
        return sha256(stripFilename(filename, strip)).toHexString(HexFormat.Default).take(12)
    }

    /** Python's `utils.hashFilesize`: SHA-256 of the decimal string, first 12 hex characters. */
    fun hashFilesize(size: String): String =
        sha256(size).toHexString(HexFormat.Default).take(12)

    /**
     * Python's `utils.sameHashed`. Two values match when their raw forms are equal (ignoring
     * case), when one side's raw form equals the other side's hash, or when both hashes are
     * equal. So a client that sends raw names agrees with a client that sends hashed names.
     */
    fun sameHashed(raw1: String, hashed1: String, raw2: String, hashed2: String): Boolean =
        raw1.lowercase() == raw2.lowercase() ||
            raw1 == raw2 ||
            raw1 == hashed2 ||
            hashed1 == raw2 ||
            hashed1 == hashed2

    /** Python's `utils.sameFilename`. [PRIVACY_HIDDENFILENAME] matches any filename. */
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
     * Python's `utils.sameFilesize`. An empty size or a size of 0 means unknown or withheld, and
     * matches any size. Sizes are always strings here, because the wire serializer decodes a
     * number or a string into a string. Python's int and str forms of a size give the same hash.
     */
    fun sameFilesize(filesize1: String?, filesize2: String?): Boolean {
        val s1 = filesize1 ?: ""
        val s2 = filesize2 ?: ""
        if (s1.isEmpty() || s2.isEmpty() || s1 == "0" || s2 == "0") return true
        return sameHashed(s1, hashFilesize(s1), s2, hashFilesize(s2))
    }

    /** Python's `utils.sameFileduration`: a rounded difference under 2.5 s is the same file. */
    fun sameFileduration(duration1: Double, duration2: Double): Boolean =
        kotlin.math.abs(duration1.roundToLong() - duration2.roundToLong()) < DIFFERENT_DURATION_THRESHOLD

    /** Python's `constants.DIFFERENT_DURATION_THRESHOLD`. */
    private const val DIFFERENT_DURATION_THRESHOLD = 2.5
}
