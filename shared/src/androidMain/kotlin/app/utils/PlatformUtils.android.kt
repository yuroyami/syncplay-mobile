package app.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ContentResolver
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalView
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.player.PlayerEngine
import app.player.exo.ExoEngine
import app.player.kite.AndroidKiteMediaResolver
import app.player.kite.KiteEngine
import app.player.mpv.MpvEngine
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
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import java.io.File
import java.lang.ref.WeakReference
import SyncplayMobile.shared.KiteBuildConfig


actual val platform: Platform = Platform.Android

/* A lazy singleton, so one OkHttp engine is shared and connection pooling works. HttpTimeout
 * fails fast on flaky CDNs, and the User-Agent header is set for CDNs that filter on it. */
actual val httpClient: HttpClient by lazy {
    HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        /* HTTP logging goes to loggy(). The host filter limits it to JSON API hosts (api.*), so
         * image bodies of 100 KB or more do not flood the log. */
        install(Logging) {
            logger = app.utils.KtorLoggyLogger
            // Every request line carries the URL, which holds the Klipy key (loggy masks it).
            // Full bodies are for debugging only, so release builds log at INFO.
            level = if (KiteBuildConfig.IS_DEBUG) LogLevel.ALL else LogLevel.INFO
            sanitizeHeader { header -> header == "Api-Key" || header == HttpHeaders.Authorization }
            filter { request -> request.url.host.startsWith("api.") }
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, "SynkplayMobile/${KiteBuildConfig.APP_VERSION}")
        }
    }
}

/** Media player engines on Android: ExoPlayer, mpv and KitePlayer. */
actual val availablePlatformPlayerEngines: List<PlayerEngine> = buildList {
    add(ExoEngine)
    add(MpvEngine)
    // One KitePlayer entry: the renderer (native view or pure Compose) is a toggle in the room.
    add(KiteEngine(AndroidKiteMediaResolver))
}

actual fun RoomViewmodel.instantiateNetworkManager(): NetworkManager {
    val preferredEngine = NETWORK_ENGINE.value()
    return when (preferredEngine) {
        "netty" -> NettyNetworkManager(this)
        else -> KtorNetworkManager(this)
    }
}

actual fun generateTimestampMillis() = System.currentTimeMillis()

/**
 * Whether this device is a TV. Android reports the mode through UiModeManager, but some TV boxes
 * have the leanback feature without reporting TV mode, so this checks both (as pull request #159
 * did). The answer is cached: it cannot change while the process lives, and each check is a
 * system service call.
 */
private val television: Boolean by lazy {
    runCatching {
        val context = contextObtainer()
        val modes = context.getSystemService(android.app.UiModeManager::class.java)
        modes?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    }.getOrDefault(false)
}

actual fun isTelevision(): Boolean = television

/** Android answers this directly, honouring both the locale and the user's own override. */
actual fun deviceUses24HourClock(): Boolean =
    runCatching { android.text.format.DateFormat.is24HourFormat(contextObtainer()) }.getOrDefault(true)

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

/** The display name of a content:// URI from the ContentResolver, or null when the query fails. */
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

@Composable
actual fun EnterRoomMode(portrait: Boolean) {
    val view = LocalView.current

    // SideEffect runs after every composition, so it hides the bars again after a popup or menu
    // closes.
    SideEffect {
        if (Build.VERSION.SDK_INT >= 30) {
            view.windowInsetsController?.hide(
                android.view.WindowInsets.Type.systemBars()
            )
        }
    }

    val activity = LocalActivity.current as? ComponentActivity

    LaunchedEffect(portrait) {
        activity?.hideSystemUI()
        activity?.requestedOrientation = if (portrait)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

@Composable
actual fun ExitRoomMode() {
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

actual fun platformDescription(): String =
    "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}, ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})"

actual fun getCacheDirectoryPath(subdir: String): String? = try {
    java.io.File(contextObtainer().cacheDir, subdir).apply { mkdirs() }.absolutePath
} catch (_: Exception) {
    null
}

actual fun appendToFile(path: String, content: String) {
    try {
        java.io.File(path).appendText(content)
    } catch (_: Exception) { }
}

actual fun writeTextFile(path: String, content: String) {
    try {
        java.io.File(path).writeText(content)
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

actual fun writeFileBytes(path: String, bytes: ByteArray) {
    try {
        val file = java.io.File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    } catch (_: Exception) { }
}

actual fun readFileBytes(path: String): ByteArray? {
    return try {
        val file = java.io.File(path)
        if (!file.exists()) null else file.readBytes()
    } catch (_: Exception) {
        null
    }
}

actual fun fileExists(path: String): Boolean = try {
    java.io.File(path).exists()
} catch (_: Exception) {
    false
}

actual fun getMpvConfFilePath(): String? {
    return try {
        val ctx = contextObtainer()
        "${ctx.filesDir.absolutePath}/mpv.conf"
    } catch (_: Exception) {
        null
    }
}

actual fun consumePendingShortcut(): app.home.JoinConfig? = null

actual fun reducedMotion(): Boolean = runCatching {
    android.provider.Settings.Global.getFloat(contextObtainer().contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

@Composable
actual fun rememberScreenReaderActive(): State<Boolean> {
    val manager = remember { contextObtainer().getSystemService(AccessibilityManager::class.java) }
    val active = remember { mutableStateOf(manager.speaksScreen()) }
    DisposableEffect(manager) {
        if (manager == null) return@DisposableEffect onDispose { }
        val update = { active.value = manager.speaksScreen() }
        val onState = AccessibilityManager.AccessibilityStateChangeListener { update() }
        val onTouch = AccessibilityManager.TouchExplorationStateChangeListener { update() }
        manager.addAccessibilityStateChangeListener(onState)
        manager.addTouchExplorationStateChangeListener(onTouch)
        // From Android 13, a switch from one service to another while a third stays on is reported too.
        val onServices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AccessibilityManager.AccessibilityServicesStateChangeListener { update() }
                .also { manager.addAccessibilityServicesStateChangeListener(it) }
        } else null
        update()
        onDispose {
            manager.removeAccessibilityStateChangeListener(onState)
            manager.removeTouchExplorationStateChangeListener(onTouch)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && onServices != null) {
                manager.removeAccessibilityServicesStateChangeListener(onServices)
            }
        }
    }
    return active
}

/**
 * TalkBack on a phone turns on touch exploration. A television has no touch screen, so there the
 * test is any enabled service that speaks. Services that only automate (password managers, for
 * example) do not count.
 */
private fun AccessibilityManager?.speaksScreen(): Boolean = this != null && isEnabled &&
    (isTouchExplorationEnabled || getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN).isNotEmpty())

actual fun localizedLanguageName(iso6391: String, inLanguage: String): String? {
    val displayIn = java.util.Locale.forLanguageTag(inLanguage)
    val locale = java.util.Locale.forLanguageTag(iso6391)
    val name = locale.getDisplayLanguage(displayIn)
    // The JDK echoes the code back when it does not know the language.
    if (name.isBlank() || name.equals(iso6391, ignoreCase = true)) return null
    return name.replaceFirstChar { it.titlecase(displayIn) }
}
