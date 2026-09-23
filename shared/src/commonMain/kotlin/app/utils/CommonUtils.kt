package app.utils

import SyncplayMobile.shared.KiteBuildConfig
import io.github.vinceglb.filekit.dialogs.FileKitType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.kotlincrypto.hash.md.MD5
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.time.Clock

/** Utility functions that are the same on every platform (no expect/actual). */

/** The app name, from the generated KiteBuildConfig (set in the root build.gradle.kts). */
val appName: String = KiteBuildConfig.APP_NAME

/** Marker annotation for protocol-related builders and scopes. */
annotation class ProtocolApi

/**
 * The current local time for a chat line, in the device's own clock format: "14:23" on a 24-hour
 * clock, "2:23 PM" otherwise. Seconds are left out, because other chat apps also stamp a line to
 * the minute.
 */
fun generateClockstamp(): String {
    val c = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
    if (deviceUses24HourClock()) return "${c.hour.fixDigits()}:${c.minute.fixDigits()}"
    val suffix = if (c.hour < 12) "AM" else "PM"
    val hour = when {
        c.hour == 0 -> 12
        c.hour > 12 -> c.hour - 12
        else -> c.hour
    }
    return "$hour:${c.minute.fixDigits()} $suffix"
}

/** Pads an int to 2 digits with a leading zero (5 -> "05"). Used by [generateClockstamp]. */
private fun Int.fixDigits() = this.toString().padStart(2, '0')

/**
 * Size limits of the shared playlist (the file list that everyone in a room follows), from the
 * reference Python server's `constants.py` (`PLAYLIST_MAX_ITEMS` and `PLAYLIST_MAX_CHARACTERS`,
 * enforced by `playlistIsValid`). The official server silently refuses a `playlistChange` over
 * these limits and sends the old playlist back. So the client must not let the user build such a
 * playlist, and the built-in server must enforce the same rule on incoming changes.
 */
const val PLAYLIST_MAX_ITEMS = 250
const val PLAYLIST_MAX_CHARACTERS = 10000

/** Python's `utils.playlistIsValid`. */
fun playlistIsValid(files: List<String>): Boolean =
    files.size <= PLAYLIST_MAX_ITEMS && files.sumOf { it.length } <= PLAYLIST_MAX_CHARACTERS

/** Supported video file extensions. */
val vidExs = listOf(
    "mp4", "3gp", "av1", "mkv", "m4v", "mov", "wmv", "flv", "avi", "webm",
    "ogg", "ogv", "mpeg", "mpg", "m2v", "ts", "mts", "m2ts", "vob",
    "divx", "xvid", "asf", "rm", "rmvb", "qt", "f4v", "mxf", "m1v",
    "3g2", "mpg2", "mpg4", "h264", "h265", "hevc", "mjpeg", "mjpg", "mod",
    "tod", "dat", "wma", "wav", "amv", "mtv", "swf"
)

/** Supported audio file extensions. */
val audioExs = listOf("mp3", "m4a", "aac", "flac", "alac", "aiff", "aif", "opus", "mka", "oga", "wv", "ape", "mp2")

/** Every media extension the app accepts. Audio plays too, with cover art or the visualizer. */
val mediaExs = vidExs + audioExs

/**
 * The FileKit file type for picking media files.
 *
 * On every platform except iOS, the full [mediaExs] list filters the picker, so only matching
 * files can be picked.
 *
 * On iOS the extension filter is left out. FileKit maps each extension to a UTType with
 * `UTType.typeWithFilenameExtension(ext)`. Uncommon video extensions (divx, xvid, rm, rmvb, amv,
 * mtv, mod, tod, dat, vob, f4v, mxf, asf, swf, h264, h265 and more) have no registered public
 * UTType, so the call returns a dynamic UTType of the form `dyn.abc123...`. On iOS 26, passing
 * dynamic UTTypes to `UIDocumentPickerViewController(forOpeningContentTypes:)` shows every file
 * dimmed and unselectable. With no extensions, FileKit uses `UTTypeItem` (every file can be
 * picked), and the engine (AVPlayer, VLCKit or KitePlayer) rejects an unsupported format later.
 */
val mediaFileKitType: FileKitType
    get() = if (platform == Platform.IOS) FileKitType.File() else FileKitType.File(extensions = mediaExs)

/**
 * Whether [filename] names a media file that the shared playlist may hold or resolve. It matches
 * every [mediaExs] extension, ignoring case. A file without a known extension does not match, so
 * folder indexing skips `.nfo`, `.jpg`, `.txt` and similar files.
 */
fun isPlayableMediaFilename(filename: String): Boolean {
    val ext = filename.substringAfterLast('.', "").lowercase()
    if (ext.isEmpty()) return false
    return ext in mediaExs
}

/** Supported subtitle and closed-caption file extensions, used as the FileKit picker filter. */
val ccExs = listOf("srt", "sub", "sbv", "ass", "ssa", "usf", "idx", "vtt", "smi", "rt", "txt")

/** Supported playlist file extensions, used as the FileKit picker filter. */
val playlistExs = listOf("txt", "m3u")

/** MD5 digest of [str] as raw bytes. Syncplay sends passwords as the hex MD5 hash. */
fun md5(str: String) = MD5().digest(str.encodeToByteArray())

/** SHA-256 digest of [str] as raw bytes. */
fun sha256(str: String) = SHA256().digest(str.encodeToByteArray())

/**
 * True if this character is in the 0x2600 to 0x27BF symbol range or is a surrogate. A `Char` is
 * one UTF-16 unit (at most 0xFFFF), so the ranges above 0xFFFF never match. An emoji above 0xFFFF
 * matches only through the surrogate check.
 */
fun Char.isEmoji(): Boolean {
    val codePoint = this.code
    return when {
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

/**
 * `substring` with [start] raised to 0 and [end] capped at the length. It still throws when
 * [start] ends up past [end].
 */
fun String.substringSafely(start: Int, end: Int) = substring(start.coerceAtLeast(0), end.coerceAtMost(length))

/** @return A random managed-room password in the "XX-###-###" format, for example "AB-123-456" */
fun generateRoomPassword(): String {
    fun letters(n: Int) = (1..n).map { ('A'..'Z').random() }.joinToString("")
    fun digits(n: Int) = (1..n).map { ('0'..'9').random() }.joinToString("")
    return "${letters(2)}-${digits(3)}-${digits(3)}"
}