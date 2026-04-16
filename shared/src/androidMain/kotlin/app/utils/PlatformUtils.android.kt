package app.utils

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
import app.player.PlayerEngine
import app.player.exo.ExoEngine
import app.player.mpv.MpvEngine
import app.player.vlc.VlcEngine
import app.preferences.Preferences.NETWORK_ENGINE
import app.preferences.value
import app.protocol.network.KtorNetworkManager
import app.protocol.network.NettyNetworkManager
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import java.lang.ref.WeakReference


actual val platform: Platform = Platform.Android

actual val httpClient: HttpClient
    get() = HttpClient(OkHttp)

/**
 * List of media player engines available on Android.
 *
 * Includes ExoPlayer, MPV, and VLC. Actual availability depends on build flavor
 * (noLibs vs withLibs).
 */
actual val availablePlatformPlayerEngines: List<PlayerEngine> =
    listOf(ExoEngine, MpvEngine, VlcEngine)

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

actual fun getDeviceIpAddress(): String? {
    return try {
        java.net.NetworkInterface.getNetworkInterfaces()?.toList()
            ?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
            ?.hostAddress
    } catch (_: Exception) {
        null
    }
}

actual fun getLogDirectoryPath(): String? {
    return try {
        val ctx = contextObtainer()
        val logDir = java.io.File(ctx.filesDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()
        logDir.absolutePath
    } catch (_: Exception) {
        null
    }
}

actual fun appendToFile(path: String, content: String) {
    try {
        java.io.File(path).appendText(content)
    } catch (_: Exception) { }
}

actual fun listFiles(directoryPath: String): List<String> {
    return try {
        java.io.File(directoryPath).listFiles()?.map { it.name } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

actual fun readFile(path: String): String {
    return try {
        java.io.File(path).readText()
    } catch (_: Exception) { "" }
}

actual fun deleteFile(path: String) {
    try {
        java.io.File(path).delete()
    } catch (_: Exception) { }
}

actual fun consumePendingShortcut(): app.home.JoinConfig? = null