package app.utils

import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import app.player.PlayerEngine
import app.player.kite.desktopKiteEngine
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
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.lang.ref.WeakReference
import SyncplayMobile.shared.KiteBuildConfig

actual val platform: Platform = Platform.Desktop

/* One lazy client, so every request shares one OkHttp engine and its connection pool.
 * It mirrors the Android actual, because OkHttp is pure JVM. */
actual val httpClient: HttpClient by lazy {
    HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        install(Logging) {
            logger = KtorLoggyLogger
            // Release builds log only the request line and status (INFO). That line holds the URL,
            // and the URL holds the KLIPY key. Full bodies are for debug builds only.
            level = if (KiteBuildConfig.IS_DEBUG) LogLevel.ALL else LogLevel.INFO
            sanitizeHeader { header -> header == "Api-Key" || header == HttpHeaders.Authorization }
            filter { request -> request.url.host.startsWith("api.") }
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, "SynkplayMobile/${KiteBuildConfig.APP_VERSION}")
        }
    }
}

/**
 * Desktop has one engine (video player): KitePlayer.
 *
 * The desktop build is a KitePlayer build, not a shell around whatever native player is
 * installed, so the distribution bundles no other player's native files.
 *
 * KiteDesktopEngine forces the engine onto the Compose canvas. KitePlayer's JVM native view
 * exists, but on macOS it takes every click meant for the controls drawn over the video.
 */
actual val availablePlatformPlayerEngines: List<PlayerEngine> = listOf(desktopKiteEngine)

actual fun RoomViewmodel.instantiateNetworkManager(): NetworkManager {
    return when (NETWORK_ENGINE.value()) {
        "ktor" -> KtorNetworkManager(this)
        // Netty is the desktop default: it is the only network engine with opportunistic TLS.
        else -> NettyNetworkManager(this)
    }
}

actual fun generateTimestampMillis() = System.currentTimeMillis()

/**
 * The desktop has no single switch for this, so the locale's own short time pattern answers it:
 * a pattern with an "a" field is a 12-hour one.
 */
actual fun deviceUses24HourClock(): Boolean = runCatching {
    val format = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, java.util.Locale.getDefault())
    val pattern = (format as? java.text.SimpleDateFormat)?.toPattern() ?: return@runCatching true
    !pattern.contains("a", ignoreCase = true)
}.getOrDefault(true)

actual fun getFolderName(uri: String): String? =
    runCatching { File(uri).name.takeIf { it.isNotBlank() } }.getOrNull()

actual fun getFileName(uri: PlatformFile): String? =
    runCatching { File(uri.path).name.takeIf { it.isNotBlank() } }.getOrNull()

actual fun getFileSize(uri: PlatformFile): Long? =
    runCatching { File(uri.path).takeIf { it.isFile }?.length() }.getOrNull()

/* On desktop the entry always comes from the system clipboard, so reading the global clipboard
 * gives the same text and avoids depending on Compose's JVM ClipEntry internals. */
actual fun ClipEntry.getText(): String? = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
}.getOrNull()

/* Desktop windows have no system bars to hide and no orientation to lock. */
@Composable
actual fun EnterRoomMode(portrait: Boolean) {
}

@Composable
actual fun ExitRoomMode() {
}

actual typealias WeakRef<T> = WeakReference<T>

actual fun <T : Any> createWeakRef(obj: T): WeakRef<T> = WeakReference(obj)

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
        val logDir = File(desktopAppDataDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()
        logDir.absolutePath
    } catch (_: Exception) {
        null
    }
}

actual fun platformDescription(): String =
    "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})"

actual fun getCacheDirectoryPath(subdir: String): String? = try {
    File(File(desktopAppDataDir, "cache"), subdir).apply { mkdirs() }.absolutePath
} catch (_: Exception) {
    null
}

actual fun appendToFile(path: String, content: String) {
    try {
        File(path).appendText(content)
    } catch (_: Exception) { }
}

actual fun writeTextFile(path: String, content: String) {
    try {
        File(path).writeText(content)
    } catch (_: Exception) { }
}

actual fun fileLength(path: String): Long = File(path).length()

actual fun listFiles(directoryPath: String): List<String> {
    return try {
        File(directoryPath).listFiles()?.map { it.name } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

actual fun readFile(path: String): String {
    return try {
        File(path).readText()
    } catch (_: Exception) { "" }
}

actual fun deleteFile(path: String) {
    try {
        File(path).delete()
    } catch (_: Exception) { }
}

actual fun writeFileBytes(path: String, bytes: ByteArray) {
    try {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    } catch (_: Exception) { }
}

actual fun readFileBytes(path: String): ByteArray? {
    return try {
        val file = File(path)
        if (!file.exists()) null else file.readBytes()
    } catch (_: Exception) {
        null
    }
}

actual fun fileExists(path: String): Boolean = try {
    File(path).exists()
} catch (_: Exception) {
    false
}

/** Where an mpv config would live on desktop. Nothing reads it here: the only desktop engine is
 *  KitePlayer, and the mpv settings rows are Android's. It exists to satisfy the expect
 *  declaration. */
actual fun getMpvConfFilePath(): String? = try {
    val dir = java.io.File(desktopAppDataDir, "mpv").apply { mkdirs() }
    java.io.File(dir, "mpv.conf").absolutePath
} catch (_: Exception) {
    null
}

/** The desktop "shortcut": the join request from the command line (--user, --room, --host...). */
actual fun consumePendingShortcut(): app.home.JoinConfig? =
    pendingDesktopJoin.also { pendingDesktopJoin = null }

actual fun reducedMotion(): Boolean = false

// A cursor made of one transparent pixel. A headless machine (a test run) has no cursors at all.
actual val hiddenPointerIcon: PointerIcon? by lazy {
    runCatching {
        val blank = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        PointerIcon(Toolkit.getDefaultToolkit().createCustomCursor(blank, Point(0, 0), "hidden"))
    }.getOrNull()
}

@Composable
actual fun rememberScreenReaderActive(): State<Boolean> = remember { mutableStateOf(false) }

/** A desktop window is never subject to overscan. */
actual fun isTelevision(): Boolean = false

actual fun localizedLanguageName(iso6391: String, inLanguage: String): String? {
    val displayIn = java.util.Locale.forLanguageTag(inLanguage)
    val locale = java.util.Locale.forLanguageTag(iso6391)
    val name = locale.getDisplayLanguage(displayIn)
    if (name.isBlank() || name.equals(iso6391, ignoreCase = true)) return null
    return name.replaceFirstChar { it.titlecase(displayIn) }
}
