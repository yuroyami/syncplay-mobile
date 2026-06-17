package app.utils

import SyncplayMobile.shared.BuildConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ClipEntry
import app.delegato
import app.player.PlayerEngine
import app.player.avplayer.AVPlayerEngine
import app.player.mpv.MpvKitEngine
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
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import platform.Foundation.NSURLCache
import platform.Foundation.NSURLRequestUseProtocolCachePolicy
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
import platform.Foundation.NSSelectorFromString
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
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskAll
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.ifaddrs.getDeviceLocalIp
import platform.posix.memcpy
import kotlin.math.roundToLong
import kotlin.native.ref.WeakReference

actual val platform: Platform = Platform.IOS

/* Lazy singleton: a fresh `get()` would mint a new Darwin engine (and NSURLSession) on every
 * access (the GIF grid does this per-tile per-scroll), and iOS throttles new sessions, so Klipy
 * stops downloading a few seconds in. One shared client avoids that.
 *
 * Defaults here apply to every caller of `httpClient` (subtitle search, Klipy downloads,
 * AnimatedImage, etc.):
 *
 *  - HttpTimeout: NSURLSession on Darwin has no default timeouts, so a flaky CDN can hang a
 *    request forever. The 15s/10s/15s envelope matches KlipyUtils' own client.
 *  - defaultRequest User-Agent: some Cloudflare-fronted CDNs block requests with no UA.
 *
 * Per-feature `HttpClient.config { … }` calls layer plugins on top without disturbing these. */
actual val httpClient: HttpClient by lazy {
    HttpClient(Darwin) {
        engine {
            /* Honor response `Cache-Control`. Ktor's Darwin engine sets
             * `NSURLRequestReloadIgnoringCacheData` on every request in `toNSUrlRequest()`, which
             * makes NSURLSession bypass NSURLCache, so cacheable GIFs (Klipy CDN sends
             * `max-age=86400`) re-download on every HUD toggle. configureRequest runs after
             * toNSUrlRequest(), so this overrides the policy back to protocol-driven caching. */
            configureRequest { setCachePolicy(NSURLRequestUseProtocolCachePolicy) }
            /* Enlarge NSURLCache (from ~4 MB mem / ~20 MB disk default) to hold a panel of GIFs
             * (24 × ~200 KB) plus scroll history. Memory cache makes HUD toggles repaint instantly;
             * disk cache survives restarts so the trending panel comes up populated. */
            configureSession {
                setURLCache(
                    NSURLCache(
                        memoryCapacity = 32uL * 1024uL * 1024uL,
                        diskCapacity = 256uL * 1024uL * 1024uL,
                        diskPath = "ktor-http-cache"
                    )
                )
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        /* HTTP transcript into loggy(), restricted to JSON API hosts (api.*) via the filter.
         * Logging image/subtitle downloads breaks them on Darwin: the plugin tees
         * `response.rawContent` into transcript and consumer channels, and on Kotlin/Native that
         * split delivers truncated bytes under load, so a GIF body comes back partial and
         * CGImageSourceCreateWithData returns null (blank tiles). API hosts cover all useful
         * diagnostics; image fetches bypass the interceptor. */
        install(Logging) {
            logger = app.utils.KtorLoggyLogger
            level = LogLevel.ALL
            sanitizeHeader { header -> header == "Api-Key" || header == HttpHeaders.Authorization }
            filter { request -> request.url.host.startsWith("api.") }
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, "SynkplayMobile/${BuildConfig.APP_VERSION}")
        }
    }
}

actual val availablePlatformPlayerEngines: List<PlayerEngine> = buildList {
    add(AVPlayerEngine)
    // MPVKit (libmpv) sits second in the home-screen picker. Listing it is safe even when MPVKit
    // isn't linked: the picker gates selection on engine.isAvailable, and MpvKitEngine.isAvailable
    // is true only once the Swift MpvKitBridge factory is registered at app startup.
    add(MpvKitEngine)
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

/**
 * Applies an orientation mask: updates the delegate's `supportedInterfaceOrientationsForWindow`
 * answer, requests the new geometry, and pokes the root view controller with
 * `setNeedsUpdateOfSupportedInterfaceOrientations()`.
 *
 * The poke is required: iOS 16+ caches the last supported-orientations answer and won't re-query
 * the delegate. A geometry request naming a concrete orientation (e.g. landscape) takes effect,
 * but an "All" mask names no target orientation, so without the poke the stale cached mask keeps
 * rotation locked after leaving a room.
 */
private fun applyOrientationMask(mask: UIInterfaceOrientationMask) {
    delegato.myOrientationMask = mask
    val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull() as? UIWindowScene
    scene?.requestGeometryUpdateWithPreferences(
        geometryPreferences = UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = mask),
        errorHandler = null
    )
    // Not in Kotlin's UIKit bindings: invoke the iOS 16+ selector dynamically.
    // respondsToSelector doubles as the availability guard on older systems.
    val rootVc = (scene?.windows?.firstOrNull() as? UIWindow)?.rootViewController
    val needsUpdate = NSSelectorFromString("setNeedsUpdateOfSupportedInterfaceOrientations")
    if (rootVc?.respondsToSelector(needsUpdate) == true) {
        rootVc.performSelector(needsUpdate)
    }
}

@Composable
actual fun EnterRoomMode(portrait: Boolean) {
    LaunchedEffect(portrait) {
        applyOrientationMask(
            if (portrait) UIInterfaceOrientationMaskPortrait else UIInterfaceOrientationMaskLandscape
        )
    }
}

@Composable
actual fun ExitRoomMode() {
    LaunchedEffect(null) {
        applyOrientationMask(UIInterfaceOrientationMaskAll)
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

actual fun writeTextFile(path: String, content: String) {
    try {
        NSString.create(string = content)
            .writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
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

actual fun fileExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)

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

/**
 * Path to the user-editable `mpv.conf` on iOS: `<Documents>/mpv.conf`. MPVKit reads it because
 * [app.player.mpv.MpvKitImpl] points mpv's `config-dir` at the Documents directory at init. Living
 * in Documents also means a user can drop in / edit the file via the Files app. Returns null only
 * if the Documents directory can't be resolved.
 */
actual fun getMpvConfFilePath(): String? {
    val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: return null
    return "$docs/mpv.conf"
}

actual fun consumePendingShortcut(): app.home.JoinConfig? {
    return app.pendingShortcutJoinConfig.value?.also {
        app.pendingShortcutJoinConfig.value = null
    }
}