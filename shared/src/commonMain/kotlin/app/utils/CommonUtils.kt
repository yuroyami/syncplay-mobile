package app.utils

import SyncplayMobile.shared.BuildConfig
import io.github.vinceglb.filekit.dialogs.FileKitType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.kotlincrypto.hash.md.MD5
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.time.Clock

/********************************************************************************
 * Collection of Kotlin/Native utility functions that work across all platforms *
 * without requiring platform-specific implementations (expect/actual).         *
 ********************************************************************************/

/** App name sourced from BuildConfig (defined once in AppConfig.kt in buildSrc) */
val appName: String = BuildConfig.APP_NAME

/** Marker annotation for protocol-related builders and scopes. */
annotation class ProtocolApi

/**
 * Generates a timestamp string in HH:MM:SS format using the current system time.
 *
 * @return Formatted time string (e.g., "14:23:05")
 */
fun generateClockstamp(): String {
    val c = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
    return "${c.hour.fixDigits()}:${c.minute.fixDigits()}:${c.second.fixDigits()}"
}

/**
 * Pads an integer to 2 digits with leading zeros.
 * This is used only by [generateClockstamp]
 *
 * @return String padded to at least 2 characters (e.g., 5 becomes "05")
 */
private fun Int.fixDigits() = this.toString().padStart(2, '0')

/**
 * List of supported video file extensions.
 * We pass this to the file selection (FileKit)
 */
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
 * List of supported subtitle/closed caption file extensions.
 * We pass this to the file selection (FileKit)
 */
val ccExs = listOf("srt", "sub", "sbv", "ass", "ssa", "usf", "idx", "vtt", "smi", "rt", "txt")

/**
 * List of supported playlist file extensions.
 * We pass this to the file selection (FileKit)
 */
val playlistExs = listOf("txt", "m3u")

/**
 * Computes the MD5 hash of a string.
 *
 * Syncplay servers accept passwords as MD5 hashes digested in hexadecimal format.
 *
 * @param str The input string to hash
 * @return The MD5 hash as a byte array
 */
fun md5(str: String) = MD5().digest(str.encodeToByteArray())

/**
 * Computes the SHA-256 hash of a string.
 *
 * @param str The input string to hash
 * @return The SHA-256 hash as a byte array
 */
fun sha256(str: String) = SHA256().digest(str.encodeToByteArray())

/**
 * Checks if a character is an emoji.
 *
 * Detects emoji characters by checking Unicode code point ranges including:
 * - Basic emoji symbols (U+2600 to U+27BF)
 * - Emoticons (U+1F600 to U+1F64F)
 * - Pictographs and symbols
 * - Surrogate pairs for extended emoji
 *
 * @return true if the character is an emoji, false otherwise
 */
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

/**
 * Extracts a substring with bounds checking to prevent IndexOutOfBoundsException.
 *
 * Automatically coerces start index to 0 and end index to string length.
 *
 * @param start The starting index (will be coerced to valid range)
 * @param end The ending index (will be coerced to valid range)
 * @return The safe substring
 */
fun String.substringSafely(start: Int, end: Int) = substring(start.coerceAtLeast(0), end.coerceAtMost(length))

/** @return random password in "XX-###-###" format (e.g. "AB-123-456") */
fun generateRoomPassword(): String {
    fun letters(n: Int) = (1..n).map { ('A'..'Z').random() }.joinToString("")
    fun digits(n: Int) = (1..n).map { ('0'..'9').random() }.joinToString("")
    return "${letters(2)}-${digits(3)}-${digits(3)}"
}