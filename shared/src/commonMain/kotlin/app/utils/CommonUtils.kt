package app.utils

import SyncplayMobile.shared.BuildConfig
import io.github.vinceglb.filekit.dialogs.FileKitType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.kotlincrypto.hash.md.MD5
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.time.Clock

/** Platform-agnostic utility functions (no expect/actual). */

/** App name sourced from BuildConfig (defined in AppConfig.kt in buildSrc). */
val appName: String = BuildConfig.APP_NAME

/** Marker annotation for protocol-related builders and scopes. */
annotation class ProtocolApi

/** Current local time as an "HH:MM:SS" string (e.g. "14:23:05"). */
fun generateClockstamp(): String {
    val c = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
    return "${c.hour.fixDigits()}:${c.minute.fixDigits()}:${c.second.fixDigits()}"
}

/** Pads an int to 2 digits with a leading zero (5 -> "05"). Used by [generateClockstamp]. */
private fun Int.fixDigits() = this.toString().padStart(2, '0')

/**
 * Shared-playlist size limits, from the reference python server's `constants.py`
 * (`PLAYLIST_MAX_ITEMS` / `PLAYLIST_MAX_CHARACTERS`, enforced via `playlistIsValid`).
 * The official server silently refuses any `playlistChange` exceeding these and sends
 * the old playlist back — so the client must not let the user build one, and our own
 * built-in server must enforce the same rule on inbound changes.
 */
const val PLAYLIST_MAX_ITEMS = 250
const val PLAYLIST_MAX_CHARACTERS = 10000

/** Python's `utils.playlistIsValid`. */
fun playlistIsValid(files: List<String>): Boolean =
    files.size <= PLAYLIST_MAX_ITEMS && files.sumOf { it.length } <= PLAYLIST_MAX_CHARACTERS

/** Supported video file extensions, used as the FileKit picker filter. */
val vidExs = listOf(
    "mp4", "3gp", "av1", "mkv", "m4v", "mov", "wmv", "flv", "avi", "webm",
    "ogg", "ogv", "mpeg", "mpg", "m2v", "ts", "mts", "m2ts", "vob",
    "divx", "xvid", "asf", "rm", "rmvb", "qt", "f4v", "mxf", "m1v", "m2v",
    "3g2", "mpg2", "mpg4", "h264", "h265", "hevc", "mjpeg", "mjpg", "mod",
    "tod", "dat", "wma", "wav", "amv", "mtv", "swf"
)

/**
 * Returns a [FileKitType.File] configured for picking video files.
 *
 * On Android, the full [vidExs] list is used as a MIME-extension filter so only matching files
 * appear selectable in the SAF picker.
 *
 * On iOS, the extensions filter is OMITTED. Reason: FileKit maps each extension to a UTType via
 * `UTType.typeWithFilenameExtension(ext)`. Uncommon video extensions (divx, xvid, rm, rmvb, amv,
 * mtv, mod, tod, dat, vob, f4v, mxf, asf, swf, h264, h265 …) have no registered public UTType,
 * so the API returns a *dynamic* UTType of the form `dyn.abc123…`. On iOS 26, passing dynamic
 * UTTypes to `UIDocumentPickerViewController(forOpeningContentTypes:)` causes the picker to
 * render every file as dimmed/unselectable. Falling back to [FileKitType.File] with `null`
 * extensions makes FileKit use `UTTypeItem` (all files pickable), and the player layer
 * (AVPlayer / VLCKit / mpv) naturally rejects unsupported formats downstream.
 */
val videoFileKitType: FileKitType
    get() = if (platform == Platform.IOS) FileKitType.File() else FileKitType.File(extensions = vidExs)

/**
 * List of supported audio file extensions. Used (together with [vidExs]) to decide whether a
 * file discovered while indexing a media directory belongs in the shared playlist.
 */
val audioExs = listOf("mp3", "m4a", "aac", "flac", "alac", "aiff", "aif", "opus", "mka", "oga", "wv", "ape", "mp2")

/**
 * Whether a filename (with extension) names a media file we are willing to put into / resolve
 * from the shared playlist. Matches video ([vidExs]) and audio ([audioExs]) extensions,
 * case-insensitively. Files without a recognized extension are ignored so directory indexing
 * doesn't pull in `.nfo`, `.jpg`, `.txt`, etc.
 */
fun isPlayableMediaFilename(filename: String): Boolean {
    val ext = filename.substringAfterLast('.', "").lowercase()
    if (ext.isEmpty()) return false
    return ext in vidExs || ext in audioExs
}

/** Supported subtitle/closed-caption file extensions, used as the FileKit picker filter. */
val ccExs = listOf("srt", "sub", "sbv", "ass", "ssa", "usf", "idx", "vtt", "smi", "rt", "txt")

/** Supported playlist file extensions, used as the FileKit picker filter. */
val playlistExs = listOf("txt", "m3u")

/** MD5 digest of [str] as raw bytes. Syncplay sends passwords as the hex MD5 hash. */
fun md5(str: String) = MD5().digest(str.encodeToByteArray())

/** SHA-256 digest of [str] as raw bytes. */
fun sha256(str: String) = SHA256().digest(str.encodeToByteArray())

/** True if this character falls in a common emoji code-point range or is a surrogate. */
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

/** substring() with [start]/[end] coerced into bounds, so it never throws. */
fun String.substringSafely(start: Int, end: Int) = substring(start.coerceAtLeast(0), end.coerceAtMost(length))

/** @return random password in "XX-###-###" format (e.g. "AB-123-456") */
fun generateRoomPassword(): String {
    fun letters(n: Int) = (1..n).map { ('A'..'Z').random() }.joinToString("")
    fun digits(n: Int) = (1..n).map { ('0'..'9').random() }.joinToString("")
    return "${letters(2)}-${digits(3)}-${digits(3)}"
}