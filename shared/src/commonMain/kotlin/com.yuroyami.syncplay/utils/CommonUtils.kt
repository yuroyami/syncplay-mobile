package com.yuroyami.syncplay.utils

import com.yuroyami.syncplay.logic.SyncplayViewmodel
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.kotlincrypto.hash.md.MD5
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.time.Clock

/*****
 * Bunch of Kotlin/Native utils that don't need to be commonized via expect/actual
 */
object CommonUtils {

    /** Generates current millisecond time */
    val timeMillis: Long
        get() = Clock.System.now().toEpochMilliseconds()

    /** Generates the current clock timestamp */
    fun generateClockstamp(): String {
        val c = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
        return "${c.hour.fixDigits()}:${c.minute.fixDigits()}:${c.second.fixDigits()}"
    }

    private fun Int.fixDigits() = this.toString().padStart(2, '0')

    suspend fun SyncplayViewmodel.beginPingUpdate() {
        while (true) {
            p.ping.value = if (p.isSocketValid()) {
                pingIcmp(p.session.serverHost, 32)
            } else null
            delay(1000)
        }
    }

    val vidExs = listOf(
        "mp4", "3gp", "av1", "mkv", "m4v", "mov", "wmv", "flv", "avi", "webm",
        "ogg", "ogv", "mpeg", "mpg", "m2v", "ts", "mts", "m2ts", "vob",
        "divx", "xvid", "asf", "rm", "rmvb", "qt", "f4v", "mxf", "m1v", "m2v",
        "3g2", "mpg2", "mpg4", "h264", "h265", "hevc", "mjpeg", "mjpg", "mod",
        "tod", "dat", "wma", "wav", "amv", "mtv", "swf"
    )

    val ccExs = listOf("srt", "sub", "sbv", "ass", "ssa", "usf", "idx", "vtt", "smi", "rt", "txt")

    val playlistExs = listOf("txt", "m3u")

    val langMap = mapOf(
        "العربية" to "ar", //Arabic
        //"Deutsch (ChatGPT)" to "de", //German
        "English" to "en", //English
        "español" to "es", //Spanish
        "Français" to "fr", //French
        //"हिन्दी (ChatGPT)" to "hi", //Hindi
        //"Italiano (ChatGPT)" to "it", //Italian
        //"日本語 (ChatGPT)" to "ja", //Japanese
        //"한국말 (ChatGPT)" to "ko", //Korean
        //"português (ChatGPT)" to "pt", //Portuguese
        "Pусский" to "ru", //Russian
        //"Türkçe (ChatGPT)" to "tr", //Turkish
        "中文" to "zh", //Chinese (Simplified)
    )

    /** Syncplay servers accept passwords in the form of MD5 hashes digested in hexadecimal */
    fun md5(str: String) = MD5().digest(str.encodeToByteArray())

    fun sha256(str: String) = SHA256().digest(str.encodeToByteArray())

    fun Char.isEmoji(): Boolean {
        val codePoint = this.code
        return when {
            // Basic emoji ranges
            codePoint in 0x2600..0x27BF -> true // Various symbols
            codePoint in 0x1F600..0x1F64F -> true // Emoticons
            codePoint in 0x1F300..0x1F5FF -> true // Misc symbols and pictographs
            codePoint in 0x1F680..0x1F6FF -> true // Transport and map
            codePoint in 0x1F700..0x1F77F -> true // Alchemical symbols
            codePoint in 0x1F780..0x1F7FF -> true // Geometric shapes
            codePoint in 0x1F800..0x1F8FF -> true // Supplemental arrows
            codePoint in 0x1F900..0x1F9FF -> true // Supplemental symbols and pictographs
            codePoint in 0x1FA00..0x1FA6F -> true // Chess symbols
            codePoint in 0x1FA70..0x1FAFF -> true // Symbols and pictographs extended-A
            this.isHighSurrogate() || this.isLowSurrogate() -> true // Surrogate pairs
            else -> false
        }
    }

    fun String.substringSafely(start: Int, end: Int) = substring(start.coerceAtLeast(0), end.coerceAtMost(length))
}