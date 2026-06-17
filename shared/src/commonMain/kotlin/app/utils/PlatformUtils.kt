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

/** expect declarations actualized per platform (Android/iOS). */

/** Current system time as Unix epoch milliseconds (UTC). */
expect fun generateTimestampMillis(): Long

/** Global platform-specific callback handler for system-level operations.
 * Must be initialized before use. */
lateinit var platformCallback: PlatformCallback


expect class WeakRef<T: Any>
expect fun <T : Any> createWeakRef(obj: T): WeakRef<T>

/** Platforms Syncplay runs on, each with a display label and brand color. */
enum class Platform(val label: String, val color: Color) {
    Android(label = "Android", color = Color(0xFF32DE84)),
    IOS(label = "iOS", color = Color(0xFFA2AAAD)),
}

/** The platform this build runs on. */
expect val platform: Platform

expect val httpClient: HttpClient

/** Media player engines available on the current platform. */
expect val availablePlatformPlayerEngines: List<PlayerEngine>

/** Builds the platform-specific [NetworkManager] for this room. */
expect fun RoomViewmodel.instantiateNetworkManager(): NetworkManager

/** Formats a duration as "mm:ss" (under 1h) or "hh:mm:ss", zero-padded. */
fun timestampFromMillis(milliseconds: Number): String {
    val secs = (milliseconds.toLong() / 1000L)
    return if (secs < 3600) {
        "${(secs / 60) % 60}:${(secs % 60).toString().padStart(2, '0')}".padStart(5, '0')
    } else {
        "${secs / 3600}:${((secs / 60) % 60).toString().padStart(2, '0')}:${(secs % 60).toString().padStart(2, '0')}".padStart(8, '0')
    }
}

/** Filename of [uri] (content:// on Android, file:// on iOS), or null if undeterminable. */
expect fun getFileName(uri: PlatformFile): String?

/** Parent folder name of a file URI, or null if undeterminable. */
expect fun getFolderName(uri: String): String?

/** Size in bytes of [uri], or null if undeterminable. */
expect fun getFileSize(uri: PlatformFile): Long?

/** Text content of a clipboard entry, or null if it holds no text. */
expect fun ClipEntry.getText(): String?

/**
 * Applies the windowing policy for the room screen: hides system UI chrome (Android) and
 * locks the interface orientation to the requested mode.
 *
 * Acts as the single source of truth for orientation changes inside the room — re-fires
 * whenever [portrait] changes so that toggling between landscape and portrait is a single
 * geometry update, not a race between two effects.
 *
 * @param portrait true to lock to portrait, false to lock to landscape
 */
@Composable
expect fun EnterRoomMode(portrait: Boolean)

/**
 * Restores the default windowing policy for screens outside the room (home, server host,
 * theme creator): system UI chrome visible (Android) and all orientations unlocked.
 */
@Composable
expect fun ExitRoomMode()


expect fun <T : Any> WeakRef<T>?.get(): T?

/** Device local (WiFi/LAN) IP, or null if unavailable. Used by server hosting to show a join IP. */
expect fun getDeviceIpAddress(): String?

/** Log directory path (Android: filesDir/logs, iOS: NSDocumentDirectory/logs). */
expect fun getLogDirectoryPath(): String?

/** Appends [content] to [path], creating the file if missing. */
expect fun appendToFile(path: String, content: String)

/**
 * Writes [content] to [path], REPLACING any existing file. Use this for downloaded
 * artifacts (e.g. subtitles) where appending to a previous copy would corrupt the file.
 */
expect fun writeTextFile(path: String, content: String)

/** Names of files in [directoryPath]. */
expect fun listFiles(directoryPath: String): List<String>

/** Full text of the file at [path], or "" if missing/unreadable. */
expect fun readFile(path: String): String

/** Deletes the file at [path]. */
expect fun deleteFile(path: String)

/**
 * Overwrites the file at [path] with [bytes]. Creates the file (and parents on Android) if
 * missing. No-op on failure. Used for mpv.conf import on Android.
 */
expect fun writeFileBytes(path: String, bytes: ByteArray)

/**
 * Reads all bytes from the file at [path], or null if it does not exist / cannot be read.
 * Used for mpv.conf export on Android.
 */
expect fun readFileBytes(path: String): ByteArray?

/**
 * Returns true if a file exists at [path]. Used to make one-time installs (e.g. the mpv libass
 * fallback font) idempotent without reading the whole file.
 */
expect fun fileExists(path: String): Boolean

/**
 * Returns the absolute path where mpv looks for its user configuration file, or null on
 * platforms where mpv does not honor a persistent config file (iOS). On Android this resolves
 * to `{filesDir}/mpv.conf` — the `config-dir` mpv is initialized with in MPVView.
 */
expect fun getMpvConfFilePath(): String?

/**
 * Returns and clears a pending shortcut JoinConfig, if any.
 * On iOS, this reads from the pending shortcut flow set by the AppDelegate.
 * On Android, this always returns null (shortcuts are handled via Intents).
 */
expect fun consumePendingShortcut(): app.home.JoinConfig?