package app.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import app.PlatformCallback
import app.player.PlayerEngine
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient

/** Declarations that each platform implements (Android, iOS, desktop, web), plus a few helpers. */

/** Current system time as Unix epoch milliseconds (UTC). */
expect fun generateTimestampMillis(): Long

/**
 * Whether the app runs on a television. A TV cuts off the outer edge of the picture (overscan),
 * so the room (the screen where a group watches together) keeps its controls inside a safe
 * margin there.
 */
expect fun isTelevision(): Boolean

/**
 * Whether the device is set to a 24-hour clock. Chat timestamps follow it, so a phone set to
 * "2:05 PM" never gets "14:05" in the room.
 */
expect fun deviceUses24HourClock(): Boolean

/**
 * The platform's [PlatformCallback], for system-level operations. Each platform sets it at
 * startup, before any code reads it.
 */
lateinit var platformCallback: PlatformCallback


expect class WeakRef<T: Any>
expect fun <T : Any> createWeakRef(obj: T): WeakRef<T>

/** The platforms that the app runs on, each with a display label and brand color. */
enum class Platform(val label: String, val color: Color) {
    Android(label = "Android", color = Color(0xFF32DE84)),
    IOS(label = "iOS", color = Color(0xFFA2AAAD)),
    Desktop(label = "Desktop", color = Color(0xFF5A7CFF)),
    Web(label = "Web", color = Color(0xFFE8A33D)),
}

/** The platform this build runs on. */
expect val platform: Platform

expect val httpClient: HttpClient

/**
 * The player engines available on this platform. An engine is one media player backend, such as
 * ExoPlayer or mpv.
 */
expect val availablePlatformPlayerEngines: List<PlayerEngine>

/** Builds the platform-specific [NetworkManager] for this room. */
expect fun RoomViewmodel.instantiateNetworkManager(): NetworkManager

/** Formats a duration as "mm:ss" (under an hour) or "hh:mm:ss", zero-padded. */
fun timestampFromMillis(milliseconds: Number): String {
    val secs = (milliseconds.toLong() / 1000L)
    return if (secs < 3600) {
        "${(secs / 60) % 60}:${(secs % 60).toString().padStart(2, '0')}".padStart(5, '0')
    } else {
        "${secs / 3600}:${((secs / 60) % 60).toString().padStart(2, '0')}:${(secs % 60).toString().padStart(2, '0')}".padStart(8, '0')
    }
}

/** Filename of [uri] (content:// on Android, file:// on iOS), or null when it is unknown. */
expect fun getFileName(uri: PlatformFile): String?

/** Parent folder name of a file URI, or null when it is unknown. */
expect fun getFolderName(uri: String): String?

/** Size in bytes of [uri], or null when it is unknown. */
expect fun getFileSize(uri: PlatformFile): Long?

/** Text content of a clipboard entry, or null if it holds no text. */
expect fun ClipEntry.getText(): String?

/**
 * Applies the window policy of the room screen: it hides the system bars (Android) and locks
 * the orientation. Desktop and the web do nothing here.
 *
 * It is the only place that changes the orientation inside the room. It runs again whenever
 * [portrait] changes, so a switch between landscape and portrait is one geometry update, not a
 * race between two effects.
 *
 * @param portrait true to lock to portrait, false to lock to landscape
 */
@Composable
expect fun EnterRoomMode(portrait: Boolean)

/**
 * Restores the default window policy for the screens outside the room (home, server host, theme
 * creator): the system bars show (Android) and every orientation is allowed.
 */
@Composable
expect fun ExitRoomMode()


expect fun <T : Any> WeakRef<T>?.get(): T?

/**
 * The device's local (Wi-Fi or LAN) IP address, or null when unavailable. Server hosting shows
 * it as the address that others join.
 */
expect fun getDeviceIpAddress(): String?

/**
 * The log folder, or null when the platform has none (the web). Android uses `filesDir/logs`,
 * iOS uses `Library/Logs/Synkplay`, and desktop uses `logs` in the app data folder.
 */
expect fun getLogDirectoryPath(): String?

/** An app-private cache folder named [subdir], created if missing. Null when there is none. */
expect fun getCacheDirectoryPath(subdir: String): String?

/** Appends [content] to [path], creating the file if missing. */
expect fun appendToFile(path: String, content: String)

/**
 * Writes [content] to [path] and replaces any existing file. Use it for a downloaded file, such
 * as a subtitle, where an append to an older copy would corrupt the file.
 */
expect fun writeTextFile(path: String, content: String)

/** Names of files in [directoryPath]. */
expect fun listFiles(directoryPath: String): List<String>

/** Full text of the file at [path], or "" when the file is missing or unreadable. */
expect fun readFile(path: String): String

/** Deletes the file at [path]. */
expect fun deleteFile(path: String)

/**
 * Overwrites the file at [path] with [bytes], and creates the file if it is missing. Android and
 * desktop also create missing parent folders. Does nothing on failure. Used for the mpv.conf
 * import, the mpv libass fallback font and the KitePlayer copy of a subtitle.
 */
expect fun writeFileBytes(path: String, bytes: ByteArray)

/**
 * All bytes of the file at [path], or null when the file is missing or cannot be read. Used for
 * the mpv.conf export on Android.
 */
expect fun readFileBytes(path: String): ByteArray?

/**
 * Whether a file exists at [path]. A one-time install (for example the mpv libass fallback font)
 * uses it to skip work that is already done, without reading the file.
 */
expect fun fileExists(path: String): Boolean

/**
 * The absolute path of mpv's user configuration file. On Android it is `mpv.conf` in `filesDir`,
 * the `config-dir` that every mpv core starts with (see `MpvImpl`). iOS and the web have no mpv
 * engine and return null. Desktop returns a path that nothing reads.
 */
expect fun getMpvConfFilePath(): String?

/**
 * Returns and clears a pending join from outside the app, if any. iOS reads the join that the
 * app delegate stored from a Quick Action or an invite link. Desktop reads the join from the
 * command line, and the web reads it from the address bar. Android always returns null, because
 * it handles shortcuts through intents.
 */
expect fun consumePendingShortcut(): app.home.JoinConfig?

/**
 * True when the system asks for less motion: iOS Reduce Motion, Android's animator scale at zero,
 * or the web's `prefers-reduced-motion` query. Always false on desktop.
 */
expect fun reducedMotion(): Boolean

/** The OS and the hardware, one line, for a bug report: "Android 15 (API 35, Google Pixel 7)". */
expect fun platformDescription(): String
/**
 * The name of a language written in [inLanguage], from a two-letter ISO 639-1 code. The app picks
 * its own display language, so the caller passes that rather than letting the OS decide.
 * Returns null when the platform has no name for it, so the caller can fall back to English.
 */
expect fun localizedLanguageName(iso6391: String, inLanguage: String): String?
