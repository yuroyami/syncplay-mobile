package com.yuroyami.syncplay.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry
import com.yuroyami.syncplay.PlatformCallback
import com.yuroyami.syncplay.managers.network.NetworkManager
import com.yuroyami.syncplay.managers.player.PlayerEngine
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import io.github.vinceglb.filekit.PlatformFile

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
enum class PLATFORM {
    /** Android mobile/tablet platform */
    Android,
    /** iOS/iPadOS platform */
    IOS,
}

/**
 * The current platform the application is running on.
 */
expect val platform: PLATFORM

/**
 * List of media player engines available on the current platform.
 * Each platform provides different player implementations (e.g., ExoPlayer on Android).
 */
expect val availablePlatformPlayerEngines: List<PlayerEngine>

/**
 * Creates a platform-specific network manager instance for the room.
 *
 * @param engine The network engine type to use (e.g., Ktor, Netty, SwiftNIO)
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
 * Performs an ICMP ping to a host and measures round-trip time.
 *
 * Sends a single ping packet and waits for response. May not work on all
 * devices (e.g., Android emulators don't support ICMP).
 *
 * @param host The hostname or IP address to ping
 * @param packet The packet size in bytes
 * @return Round-trip time in milliseconds, or null if ping failed
 */
expect suspend fun pingIcmp(host: String, packet: Int): Int?

/**
 * Extracts text content from a clipboard entry.
 *
 * @return The text content, or null if the entry doesn't contain text
 */
expect fun ClipEntry.getText(): String?

/**
 * Hides system UI bars (status bar and navigation bar) for immersive mode.
 *
 * Called from a Composable context. Platform behavior may vary.
 */
@Composable
expect fun HideSystemBars()

/**
 * Hides system UI bars (status bar and navigation bar) for immersive mode.
 *
 * Called from a Composable context. Platform behavior may vary.
 */
@Composable
expect fun ShowSystemBars()


expect fun <T : Any> WeakRef<T>?.get(): T?