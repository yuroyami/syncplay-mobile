package com.yuroyami.syncplay.utils

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.yuroyami.syncplay.managers.network.KtorNetworkManager
import com.yuroyami.syncplay.managers.network.NettyNetworkManager
import com.yuroyami.syncplay.managers.network.NetworkManager
import com.yuroyami.syncplay.managers.player.AndroidPlayerEngine
import com.yuroyami.syncplay.managers.player.PlayerEngine
import com.yuroyami.syncplay.managers.preferences.Preferences.NETWORK_ENGINE
import com.yuroyami.syncplay.managers.preferences.get
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * Android platform identifier for platform-specific implementations.
 */
actual val platform: PLATFORM = PLATFORM.Android

/**
 * List of media player engines available on Android.
 *
 * Includes ExoPlayer, MPV, and VLC. Actual availability depends on build flavor
 * (noLibs vs withLibs).
 */
actual val availablePlatformPlayerEngines: List<PlayerEngine> = listOf(AndroidPlayerEngine.Exoplayer, AndroidPlayerEngine.Mpv, AndroidPlayerEngine.VLC)

/**
 * Creates a platform-specific network manager instance for Android.
 *
 * @param engine The requested network engine type
 * @return NettyNetworkManager for NETTY engine, KtorNetworkManager for others
 */
actual fun RoomViewmodel.instantiateNetworkManager(): NetworkManager {
    val preferredEngine = NETWORK_ENGINE.get()
    return when (preferredEngine) {
        "netty" -> NettyNetworkManager(this)
        else -> KtorNetworkManager(this)
    }
}

/**
 * Gets the maximum volume level for media playback from Android AudioManager.
 *
 * TODO: Don't do this EVERY RECOMPOSITION - consider caching or hoisting
 *
 * @return Maximum volume value for the music stream
 */
@Composable
actual fun getSystemMaxVolume(): Int {
    val audioManager = LocalContext.current.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
}

/**
 * Gets the current system time in milliseconds since Unix epoch.
 *
 * @return Current timestamp in milliseconds
 */
actual fun generateTimestampMillis() = System.currentTimeMillis()

/**
 * Extracts the folder name from a document tree URI.
 *
 * Uses Android's Storage Access Framework to resolve the folder name from
 * a tree URI obtained via document picker.
 *
 * @param uri Document tree URI as a string
 * @return The folder name, or null if it cannot be determined
 */
actual fun getFolderName(uri: String): String? {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        uri.toUri(),
        DocumentsContract.getTreeDocumentId(uri.toUri())
    )

    val context = contextObtainer.invoke()
    val d = DocumentFile.fromTreeUri(context, childrenUri)
    return d?.name
}

/**
 * Extracts the filename from a file URI.
 *
 * Handles both content:// URIs (from Storage Access Framework) and file:// URIs.
 * For content URIs, queries the ContentResolver to get the display name.
 *
 * @param uri File URI as a string
 * @return The filename, or null if it cannot be determined
 */
actual fun getFileName(uri: String): String? {
    val actualuri = uri.toUri()
    val context = contextObtainer.invoke()
    return when (actualuri.scheme) {
        ContentResolver.SCHEME_CONTENT -> context.getContentFileName(actualuri)
        else -> actualuri.path?.let(::File)?.name
    }
}

/**
 * Gets the size of a file from its URI.
 *
 * Uses DocumentFile to query the file size through the Storage Access Framework.
 *
 * @param uri File URI as a string
 * @return File size in bytes, or null if it cannot be determined
 */
actual fun getFileSize(uri: String): Long? {
    val context = contextObtainer()
    val df = DocumentFile.fromSingleUri(context, uri.toUri()) ?: return null
    return df.length()
}

/**
 * Queries the display name of a content:// URI from the ContentResolver.
 *
 * Helper function for [getFileName] that handles content URIs specifically.
 *
 * @receiver Context for accessing ContentResolver
 * @param uri The content URI to query
 * @return The display name from the content provider, or null if query fails
 */
private fun Context.getContentFileName(uri: Uri): String? = runCatching {
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        cursor.moveToFirst()
        return@use cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
            .let(cursor::getString)
    }
}.getOrNull()


/**
 * Performs an ICMP ping to a host using Android's system ping utility.
 *
 * Executes the system ping command and parses the output to extract round-trip time.
 * Returns null if the ping fails (100% packet loss) or if the command cannot be executed.
 *
 * Note: May not work on all devices, particularly emulators that restrict ping access.
 *
 * @param host The hostname or IP address to ping
 * @param packet The packet size in bytes
 * @return Round-trip time in milliseconds, or null if ping failed
 */
actual suspend fun pingIcmp(host: String, packet: Int): Int? {
    try {
        val pingprocess = Runtime.getRuntime().exec("/system/bin/ping -c 1 -w 1 -s $packet $host") ?: return null
        val inputStream = BufferedReader(InputStreamReader(pingprocess.inputStream))
        val pingOutput = inputStream.use { it.readText() }

        return if (pingOutput.contains("100% packet loss")) {
            null
        } else {
            pingOutput.substringAfter("time=").substringBefore(" ms").trim()
                .toDouble().roundToInt()
        }
    } catch (e: Exception) {
        loggy(e.stackTraceToString())
        return null
    }
}

/**
 * Extracts text content from a ClipEntry (clipboard data).
 *
 * @receiver The ClipEntry to extract text from
 * @return The text content, or null if the entry doesn't contain text
 */
actual fun ClipEntry.getText(): String? {
    return this.clipData.getItemAt(0).text?.toString()
}

/**
 * Hides system bars (status bar and navigation bar) for fullscreen/immersive mode.
 *
 * Uses SideEffect to ensure the system bars are hidden after composition completes,
 * which is important for dialogs and popups that need to maintain fullscreen mode.
 *
 * Only works on Android 11 (API 30) and above using the modern WindowInsetsController API.
 */
@Composable
actual fun HideSystemBars() {
    val view = LocalView.current

    //We use a side effect here because it is guaranteed to be executed after every composition is concluded, which means right after a popup finishes appearing.
    SideEffect {
        if (Build.VERSION.SDK_INT >= 30) {
            view.windowInsetsController?.hide(
                android.view.WindowInsets.Type.systemBars()
            )
        }
    }

    val activity = LocalActivity.current as? ComponentActivity

    LaunchedEffect(null) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.hideSystemUI()
    }
}

@Composable
actual fun ShowSystemBars() {
    val view = LocalView.current

    val activity = LocalActivity.current as? ComponentActivity

    LaunchedEffect(null) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        activity?.showSystemUI()
        activity?.applyActivityUiProperties()

    }
}

actual typealias WeakRef<T> = java.lang.ref.WeakReference<T>

actual fun <T : Any> createWeakRef(obj: T): WeakRef<T> {
    return WeakReference(obj)
}