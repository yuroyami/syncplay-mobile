package com.yuroyami.syncplay.utils

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ActivityInfo
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
import androidx.compose.ui.platform.LocalView
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.media3.session.MediaSession
import com.yuroyami.syncplay.managers.network.KtorNetworkManager
import com.yuroyami.syncplay.managers.network.NettyNetworkManager
import com.yuroyami.syncplay.managers.network.NetworkManager
import com.yuroyami.syncplay.managers.player.VideoEngine
import com.yuroyami.syncplay.managers.player.exo.ExoplayerEngine
import com.yuroyami.syncplay.managers.player.mpv.MpvEngine
import com.yuroyami.syncplay.managers.player.vlc.VlcEngine
import com.yuroyami.syncplay.managers.preferences.Preferences.NETWORK_ENGINE
import com.yuroyami.syncplay.managers.preferences.value
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import kotlin.math.roundToInt


actual val platform: PLATFORM = PLATFORM.Android

/**
 * List of media player engines available on Android.
 *
 * Includes ExoPlayer, MPV, and VLC. Actual availability depends on build flavor
 * (noLibs vs withLibs).
 */
actual val availablePlatformVideoEngines: List<VideoEngine> =
    listOf(ExoplayerEngine, MpvEngine, VlcEngine)

actual typealias GlobalPlayerSession = MediaSession

actual fun RoomViewmodel.instantiateNetworkManager(): NetworkManager {
    val preferredEngine = NETWORK_ENGINE.value()
    return when (preferredEngine) {
        "netty" -> NettyNetworkManager(this)
        else -> KtorNetworkManager(this)
    }
}

actual fun generateTimestampMillis() = System.currentTimeMillis()

actual fun getFolderName(uri: String): String? {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        uri.toUri(),
        DocumentsContract.getTreeDocumentId(uri.toUri())
    )

    val context = contextObtainer.invoke()
    val d = DocumentFile.fromTreeUri(context, childrenUri)
    return d?.name
}

actual fun getFileName(uri: PlatformFile): String? {
    val actualuri = uri.path.toUri()
    val context = contextObtainer.invoke()
    return when (actualuri.scheme) {
        ContentResolver.SCHEME_CONTENT -> context.getContentFileName(actualuri)
        else -> actualuri.path?.let(::File)?.name
    }
}

actual fun getFileSize(uri: PlatformFile): Long? {
    val context = contextObtainer()
    val df = DocumentFile.fromSingleUri(context, uri.path.toUri()) ?: return null
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
        activity?.hideSystemUI()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

@Composable
actual fun ShowSystemBars() {
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

actual fun <T : Any> WeakRef<T>?.get(): T? = this?.get()