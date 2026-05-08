package app.utils

import SyncplayMobile.shared.BuildConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ClipEntry
import app.delegato
import app.player.PlayerEngine
import app.player.avplayer.AVPlayerEngine
import app.player.vlc.VlcKitEngine
import app.preferences.Preferences.NETWORK_ENGINE
import app.preferences.value
import app.protocol.network.KtorNetworkManager
import app.protocol.network.NetworkManager
import app.protocol.network.instantiateSwiftNioNetworkManager
import app.room.RoomViewmodel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeData
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskAll
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.ifaddrs.getDeviceLocalIp
import platform.posix.memcpy
import kotlin.math.roundToLong
import kotlin.native.ref.WeakReference

actual val platform: Platform = Platform.IOS

/* Cached singleton — `get()` would mint a fresh Darwin engine (and a backing NSURLSession)
 * on every access, which the GIF grid hits per-tile per-scroll. The leak shows up as iOS
 * throttling new sessions a few seconds in, surfacing as "Klipy works for a moment, then
 * stops downloading anything."
 *
 * Defaults installed here apply to every caller of `httpClient` (subtitle search, Klipy
 * media downloads, AnimatedImage, etc.):
 *
 *  - HttpTimeout: NSURLSession on Darwin has no sensible defaults — a flaky CDN can leave
 *    a request hanging indefinitely instead of failing fast. The 15s/10s/15s envelope
 *    matches what KlipyUtils already configured for its own (configured-copy) client.
 *  - defaultRequest with User-Agent: Cloudflare-fronted CDNs (static.klipy.com,
 *    OpenSubtitles, NewPipe peers) sometimes block requests with no UA. Setting one
 *    is harmless when the CDN doesn't care.
 *
 * Per-feature `HttpClient.config { … }` calls layer additional plugins on top without
 * unsettling these defaults. */
actual val httpClient: HttpClient by lazy {
    HttpClient(Darwin) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, "SynkplayMobile/${BuildConfig.APP_VERSION}")
        }
    }
}

actual val availablePlatformPlayerEngines: List<PlayerEngine> = buildList {
    add(AVPlayerEngine)
    add(VlcKitEngine)
}

actual fun RoomViewmodel.instantiateNetworkManager(): NetworkManager {
    val preferredEngine = NETWORK_ENGINE.value()
    return when (preferredEngine) {
        "swiftnio" -> instantiateSwiftNioNetworkManager!!(this)
        else -> KtorNetworkManager(this)
    }
}

actual fun generateTimestampMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000.0).roundToLong()
}

actual fun getFileName(uri: PlatformFile): String? {
    return uri.nsUrl.lastPathComponent
}

actual fun getFolderName(uri: String): String? {
    return NSURL.fileURLWithPath(uri).lastPathComponent
}

actual fun getFileSize(uri: PlatformFile): Long? {
    return uri.nsUrl.accessSecurely {
        val fileManager = NSFileManager.defaultManager
        val fileAttributes = fileManager.attributesOfItemAtPath(uri.path, null) ?: return null

        val fileSize = fileAttributes[NSFileSize] as? NSNumber
        val fileSizeFallback = fileAttributes["NSFileSize"] as? NSNumber

        fileSize?.longLongValue ?: fileSizeFallback?.longLongValue ?: fileSize?.longValue ?: fileSizeFallback?.longValue
    }
}

actual fun ClipEntry.getText(): String? {
    return this.getPlainText()
}

@Composable
actual fun EnterRoomMode(portrait: Boolean) {
    LaunchedEffect(portrait) {
        val mask = if (portrait) UIInterfaceOrientationMaskPortrait else UIInterfaceOrientationMaskLandscape
        delegato.myOrientationMask = mask
        (UIApplication.sharedApplication.connectedScenes.firstOrNull() as? UIWindowScene)
            ?.requestGeometryUpdateWithPreferences(
                geometryPreferences = UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = mask),
                errorHandler = null
            )
    }
}

@Composable
actual fun ExitRoomMode() {
    LaunchedEffect(null) {
        delegato.myOrientationMask = UIInterfaceOrientationMaskAll
        (UIApplication.sharedApplication.connectedScenes.firstOrNull() as? UIWindowScene)
            ?.requestGeometryUpdateWithPreferences(
                geometryPreferences = UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = UIInterfaceOrientationMaskAll),
                errorHandler = null
            )
    }
}

actual typealias WeakRef<T> = WeakReference<T>
actual fun <T : Any> createWeakRef(obj: T): WeakRef<T> {
    return WeakReference(obj)
}
actual fun <T : Any> WeakRef<T>?.get(): T? = this?.get()

actual fun getDeviceIpAddress(): String? {
    return try {
        getDeviceLocalIp()?.toKString()
    } catch (_: Exception) {
        null
    }
}

actual fun getLogDirectoryPath(): String? {
    return try {
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val docDir = paths.firstOrNull() as? String ?: return null
        val logDir = "$docDir/logs"
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(logDir)) {
            fm.createDirectoryAtPath(logDir, withIntermediateDirectories = true, attributes = null, error = null)
        }
        logDir
    } catch (_: Exception) {
        null
    }
}

actual fun appendToFile(path: String, content: String) {
    try {
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(path)) {
            fm.createFileAtPath(path, contents = null, attributes = null)
        }
        val handle = NSFileHandle.fileHandleForWritingAtPath(path) ?: return
        handle.seekToEndOfFile()
        val nsString = NSString.create(string = content)
        val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return
        handle.writeData(data)
        handle.closeFile()
    } catch (_: Exception) { }
}

actual fun listFiles(directoryPath: String): List<String> {
    return try {
        val fm = NSFileManager.defaultManager
        (fm.contentsOfDirectoryAtPath(directoryPath, error = null) as? List<*>)
            ?.filterIsInstance<String>() ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

actual fun readFile(path: String): String {
    return try {
        val fm = NSFileManager.defaultManager
        val data = fm.contentsAtPath(path) ?: return ""
        val nsString = NSString.create(data = data, encoding = NSUTF8StringEncoding) ?: return ""
        nsString.toString()
    } catch (_: Exception) { "" }
}

actual fun deleteFile(path: String) {
    try {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    } catch (_: Exception) { }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
actual fun writeFileBytes(path: String, bytes: ByteArray) {
    try {
        val nsData: NSData = if (bytes.isEmpty()) {
            NSData.create(bytes = null, length = 0uL)
        } else {
            bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
        }
        nsData.writeToFile(path, atomically = true)
    } catch (_: Exception) { }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
actual fun readFileBytes(path: String): ByteArray? {
    return try {
        val data = NSFileManager.defaultManager.contentsAtPath(path) ?: return null
        val length = data.length.toInt()
        if (length == 0) return ByteArray(0)
        val out = ByteArray(length)
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, length.toULong())
        }
        out
    } catch (_: Exception) {
        null
    }
}

/** mpv on iOS does not read a user-editable mpv.conf file, so import/export is unsupported. */
actual fun getMpvConfFilePath(): String? = null

actual fun consumePendingShortcut(): app.home.JoinConfig? {
    return app.pendingShortcutJoinConfig.value?.also {
        app.pendingShortcutJoinConfig.value = null
    }
}