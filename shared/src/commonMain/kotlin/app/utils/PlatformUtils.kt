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

/********************************************************************************
 * Collection of platform-specific utility functions that need to be actualized *
 *                   on every platform (Android/iOS)                            *
 ********************************************************************************/

/** Generates the current system time as Unix epoch milliseconds.
 * @return Milliseconds since January 1, 1970, 00:00:00 UTC */
expect fun generateTimestampMillis(): Long

/** Global platform-specific callback handler for system-level operations.
 * Must be initialized before use. */
lateinit var platformCallback: PlatformCallback


expect class WeakRef<T: Any>
expect fun <T : Any> createWeakRef(obj: T): WeakRef<T>

/**
 * Enumeration of supported platforms where Syncplay can run.
 */
enum class Platform(val label: String, val color: Color) {
    Android(label = "Android", color = Color(0xFF32DE84)),
    IOS(label = "iOS", color = Color(0xFFA2AAAD)),
}

/**
 * The current platform the application is running on.
 */
expect val platform: Platform

expect val httpClient: HttpClient

/**
 * List of media player engines available on the current platform.
 * Each platform provides different player implementations (e.g., ExoPlayer on Android).
 */
expect val availablePlatformPlayerEngines: List<PlayerEngine>

/**
 * Creates a platform-specific network manager instance for the room.
 *
 * @return Platform-specific NetworkManager implementation
 */
expect fun RoomViewmodel.instantiateNetworkManager(): NetworkManager

/**
 * Converts milliseconds to a human-readable timestamp in mm:ss or hh:mm:ss format.
 *
 * Automatically adjusts format based on duration:
 * - Less than 1 hour: "mm:ss" (e.g., "05:23")
 * - 1 hour or more: "hh:mm:ss" (e.g., "1:05:23")
 *
 * @param milliseconds Duration in milliseconds
 * @return Formatted time string with zero-padding
 */
fun timestampFromMillis(milliseconds: Number): String {
    val secs = (milliseconds.toLong() / 1000L)
    return if (secs < 3600) {
        "${(secs / 60) % 60}:${(secs % 60).toString().padStart(2, '0')}".padStart(5, '0')
    } else {
        "${secs / 3600}:${((secs / 60) % 60).toString().padStart(2, '0')}:${(secs % 60).toString().padStart(2, '0')}".padStart(8, '0')
    }
}

/**
 * Extracts the filename from a file URI.
 *
 * Platform-specific implementation handles different URI schemes
 * (content:// on Android, file:// on iOS).
 *
 * @param uri The file URI string
 * @return The filename, or null if it cannot be determined
 */
expect fun getFileName(uri: PlatformFile): String?

/**
 * Extracts the parent folder name from a file URI.
 *
 * @param uri The file URI string
 * @return The folder name, or null if it cannot be determined
 */
expect fun getFolderName(uri: String): String?

/**
 * Gets the size in bytes of a file from its URI.
 *
 * @param uri The file URI string
 * @return The file size in bytes, or null if it cannot be determined
 */
expect fun getFileSize(uri: PlatformFile): Long?

/**
 * Extracts text content from a clipboard entry.
 *
 * @return The text content, or null if the entry doesn't contain text
 */
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

/**
 * Returns the device's local (WiFi/LAN) IP address, or null if unavailable.
 * Used by the server hosting feature to show users what IP to connect to.
 */
expect fun getDeviceIpAddress(): String?

/**
 * Returns the platform-specific directory path for storing log files.
 * On Android: context.filesDir/logs
 * On iOS: NSDocumentDirectory/logs
 */
expect fun getLogDirectoryPath(): String?

/**
 * Appends a line to the specified file path. Creates the file if it doesn't exist.
 */
expect fun appendToFile(path: String, content: String)

/**
 * Lists files in the given directory, returning their names.
 */
expect fun listFiles(directoryPath: String): List<String>

/**
 * Reads the entire text content of a file at the given path.
 * Returns an empty string if the file does not exist or cannot be read.
 */
expect fun readFile(path: String): String

/**
 * Deletes the file at the given path.
 */
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