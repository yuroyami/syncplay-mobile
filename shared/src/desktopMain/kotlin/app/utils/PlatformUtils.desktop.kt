package app.utils

import androidx.compose.runtime.Composable
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
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.lang.ref.WeakReference
import SyncplayMobile.shared.KiteBuildConfig

actual val platform: Platform = Platform.Desktop

/* Lazily cached singleton so one OkHttp engine is shared and connection pooling works.
 * Mirrors the Android actual — OkHttp is pure JVM. */
actual val httpClient: HttpClient by lazy {
    HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        install(Logging) {
            logger = KtorLoggyLogger
            level = LogLevel.ALL
            sanitizeHeader { header -> header == "Api-Key" || header == HttpHeaders.Authorization }
            filter { request -> request.url.host.startsWith("api.") }
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, "SynkplayMobile/${KiteBuildConfig.APP_VERSION}")
        }
    }
}

/**
 * Desktop has ONE engine, and it is KitePlayer.
 *
 * vlcj and libmpv are gone from here on the owner's instruction: the desktop build is a KitePlayer
 * build, not a shell around whatever native player happens to be installed. That also takes their
 * bundled natives out of the distribution.
 *
 * The engine renders through the pure-Compose path, which on the JVM is not a choice: KitePlayer's
 * native-surface path compiles for the JVM but draws nothing, because desktop has no equivalent of
 * a SurfaceView to hand it. See KiteDesktopEngine.
 */
actual val availablePlatformPlayerEngines: List<PlayerEngine> = listOf(desktopKiteEngine)

actual fun RoomViewmodel.instantiateNetworkManager(): NetworkManager {
    return when (NETWORK_ENGINE.value()) {
        "ktor" -> KtorNetworkManager(this)
        // Netty is the desktop default — the only engine with opportunistic TLS.
        else -> NettyNetworkManager(this)
    }
}

actual fun generateTimestampMillis() = System.currentTimeMillis()

actual fun getFolderName(uri: String): String? =
    runCatching { File(uri).name.takeIf { it.isNotBlank() } }.getOrNull()

actual fun getFileName(uri: PlatformFile): String? =
    runCatching { File(uri.path).name.takeIf { it.isNotBlank() } }.getOrNull()

actual fun getFileSize(uri: PlatformFile): Long? =
    runCatching { File(uri.path).takeIf { it.isFile }?.length() }.getOrNull()

/* The entry always originates from the system clipboard on desktop, so reading the global
 * clipboard is equivalent and avoids depending on Compose's JVM ClipEntry internals. */
actual fun ClipEntry.getText(): String? = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
}.getOrNull()

/* Desktop windows have no system-bar chrome to hide and no orientation to lock. */
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

/** mpv's user config file — the desktop mpv engine runs with config=yes and this config-dir,
 *  so mpv.conf import/export and the libass subfont install work like on Android. */
actual fun getMpvConfFilePath(): String? = try {
    val dir = java.io.File(desktopAppDataDir, "mpv").apply { mkdirs() }
    java.io.File(dir, "mpv.conf").absolutePath
} catch (_: Exception) {
    null
}

/** Desktop "shortcut" = command-line join args parsed in Main.kt (--user/--room/--host/...). */
actual fun consumePendingShortcut(): app.home.JoinConfig? =
    pendingDesktopJoin.also { pendingDesktopJoin = null }

actual fun reducedMotion(): Boolean = false
