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
        engine {
            /* Honor `Cache-Control` from the response. Ktor's Darwin engine slaps
             * `NSURLRequestReloadIgnoringCacheData` on every NSMutableURLRequest in
             * `toNSUrlRequest()` — see ktor-client-darwin/internal/DarwinRequestUtils.kt.
             * That makes NSURLSession bypass NSURLCache entirely, so every GIF in the
             * panel re-downloads over the network on every HUD toggle even though Klipy's
             * CDN sends `Cache-Control: public, max-age=86400`. configureRequest runs
             * AFTER toNSUrlRequest(), so we can override the policy back to the protocol-
             * driven default. Now repeat opens of the GIF panel pull from NSURLSession's
             * disk cache and the tiles paint instantly. */
            configureRequest { setCachePolicy(NSURLRequestUseProtocolCachePolicy) }
            /* Bump NSURLCache from the default ~20 MB disk / 4 MB memory to something
             * that comfortably holds a panel-worth of GIFs (24 × ~200 KB ≈ 5 MB) plus
             * scrolling history. Memory cache lets HUD toggles paint instantly; disk
             * cache survives app restarts so the trending panel comes up populated. */
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
        /* Full HTTP transcript piped into loggy(). Restricted to JSON API endpoints
         * via filter — including image/subtitle download URLs in the logger breaks
         * those downloads on Darwin: the plugin tees `response.rawContent` into two
         * channels (one for the printed transcript, one for the consumer), and the
         * binary-body filter peeks the first 1024 bytes to detect that the body is
         * binary. On Kotlin/Native that split misbehaves under load — the consumer
         * side receives truncated bytes, so `AnimatedImage`'s `httpClient.get(url).body()`
         * returns a partial GIF and CGImageSourceCreateWithData returns null. The
         * symptom is "GIF panel populates with 24 results but tiles are blank."
         *
         * Logging API hosts only is enough for diagnostics — we still see request
         * URLs, status codes, headers, and JSON bodies for everything that matters.
         * Image fetches go through the engine without interception. */
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
    // MPVKit (libmpv) is the iOS default and sits second in the home-screen picker. Listing it is
    // safe even when MPVKit isn't linked: the picker gates selection on engine.isAvailable (see
    // HomeScreen), and MpvKitEngine.isAvailable is true only once the Swift MpvKitBridge factory
    // has been registered at app startup.
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
 * Applies an orientation mask: updates the delegate's answer for
 * `supportedInterfaceOrientationsForWindow`, requests the new geometry, and — crucially —
 * pokes the root view controller with `setNeedsUpdateOfSupportedInterfaceOrientations()`.
 *
 * Without that poke, iOS 16+ CACHES the last supported-orientations answer and never
 * re-queries the delegate. Forcing landscape for the room worked (the geometry request
 * names a concrete orientation), but unlocking on exit silently didn't: a request with
 * mask "All" names no target orientation, the system keeps the current geometry, and —
 * with the stale cached mask still in force — rotation stays dead. Symptom: leave a room
 * once and the app is stuck in landscape until process death.
 */
private fun applyOrientationMask(mask: UIInterfaceOrientationMask) {
    delegato.myOrientationMask = mask
    val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull() as? UIWindowScene
    scene?.requestGeometryUpdateWithPreferences(
        geometryPreferences = UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = mask),
        errorHandler = null
    )
    // Not exposed in Kotlin's UIKit bindings yet — invoke the iOS 16+ selector dynamically.
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