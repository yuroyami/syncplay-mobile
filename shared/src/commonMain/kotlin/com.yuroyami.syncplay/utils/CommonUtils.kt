package com.yuroyami.syncplay.utils

import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.kotlincrypto.hash.md.MD5
import org.kotlincrypto.hash.sha2.SHA256

/*****
 * Bunch of Kotlin/Native utils that don't need to be commonized via expect/actual
 */
object CommonUtils {

    /** Generates the current clock timestamp */
    fun generateClockstamp(): String {
        val c = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
        return "${c.hour.fixDigits()}:${c.minute.fixDigits()}:${c.second.fixDigits()}"
    }

    private fun Int.fixDigits() = this.toString().padStart(2, '0')

    suspend fun beginPingUpdate() {
        while (true) {
            viewmodel?.p?.ping?.value = if (viewmodel?.p?.isSocketValid() == true) { //if (p.connection?.socket?.isActive == true && p.connection != null) {
                pingIcmp(viewmodel!!.p.session.serverHost, 32)
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

    /** Hex Digester for hashers **/
    fun ByteArray.toHex(): String {
        //return joinToString(separator = "") { byte -> "%02x".format(byte) }

        val hexChars = "0123456789ABCDEF".toCharArray()
        val result = CharArray(size * 2)
        var index = 0
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            result[index++] = hexChars[value shr 4]
            result[index++] = hexChars[value and 0x0F]
        }
        return result.concatToString()
    }
}