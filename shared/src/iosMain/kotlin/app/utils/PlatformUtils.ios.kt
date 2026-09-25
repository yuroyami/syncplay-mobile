package app.utils

import SyncplayMobile.shared.KiteBuildConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import app.delegato
import app.player.PlayerEngine
import app.player.avplayer.AVPlayerEngine
import app.player.kite.IosKiteMediaResolver
import app.player.kite.KiteEngine
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
import platform.Foundation.NSCachesDirectory
import platform.UIKit.UIDevice
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSLibraryDirectory
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
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityIsVoiceOverRunning
import platform.UIKit.UIAccessibilityVoiceOverStatusDidChangeNotification
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
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleLanguageCode
import platform.Foundation.currentLocale
import kotlin.math.roundToLong
import kotlin.native.ref.WeakReference

actual val platform: Platform = Platform.IOS

/* Lazy singleton. A fresh `get()` would create a new Darwin engine (and NSURLSession) on every
 * access, which the GIF grid does per tile and per scroll. iOS throttles new sessions, so Klipy
 * (the GIF service) stops downloading after a few seconds. One shared client avoids that.
 *
 * These defaults apply to every caller of `httpClient` (subtitle search, Klipy downloads,
 * AnimatedImage and others):
 *
 *  - HttpTimeout: NSURLSession on Darwin has no default timeouts, so a flaky CDN can hang a
 *    request forever. The limits are 15 s per request, 10 s to connect and 15 s without data.
 *  - defaultRequest User-Agent: some CDNs behind Cloudflare block requests with no User-Agent.
 *
 * A feature's own `HttpClient.config { … }` call adds plugins on top without changing these. */
actual val httpClient: HttpClient by lazy {
    HttpClient(Darwin) {
        engine {
            /* Respect the response's `Cache-Control`. Ktor's Darwin engine sets
             * `NSURLRequestReloadIgnoringCacheData` on every request in `toNSUrlRequest()`, which
             * makes NSURLSession skip NSURLCache, so cacheable GIFs (the Klipy CDN sends
             * `max-age=86400`) download again on every HUD toggle. configureRequest runs after
             * toNSUrlRequest(), so this sets the policy back to protocol-driven caching. */
            configureRequest { setCachePolicy(NSURLRequestUseProtocolCachePolicy) }
            /* Enlarge NSURLCache (default about 4 MB in memory and 20 MB on disk) to hold a panel
             * of GIFs (24 × about 200 KB) plus the scroll history. The memory cache makes a HUD
             * toggle repaint at once. The disk cache survives restarts, so the trending panel
             * opens already filled. */
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
        /* Writes an HTTP transcript to loggy(), for JSON API hosts (api.*) only, through the
         * filter. Logging image or subtitle downloads breaks them on Darwin: the plugin copies
         * `response.rawContent` into a transcript channel and a consumer channel, and on
         * Kotlin/Native that split delivers truncated bytes under load. A GIF body then comes
         * back partial, and CGImageSourceCreateWithData returns null (blank tiles). API hosts
         * cover all useful diagnostics, and image fetches skip the interceptor. */
        install(Logging) {
            logger = app.utils.KtorLoggyLogger
            // Release builds log the request line only. That line carries the URL, which holds
            // the Klipy key (loggy masks it). Full bodies are a debugging tool, not for release.
            level = if (KiteBuildConfig.IS_DEBUG) LogLevel.ALL else LogLevel.INFO
            sanitizeHeader { header -> header == "Api-Key" || header == HttpHeaders.Authorization }
            filter { request -> request.url.host.startsWith("api.") }
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, "SynkplayMobile/${KiteBuildConfig.APP_VERSION}")
        }
    }
}

/** The engines (video players the app can drive) on iOS: AVPlayer, VLCKit and KitePlayer. */
actual val availablePlatformPlayerEngines: List<PlayerEngine> = buildList {
    add(AVPlayerEngine)
    add(VlcKitEngine)
    // KitePlayer: the same KiteImpl that ends the Android list, so both phone platforms share
    // one implementation. The renderer (native view or pure Compose) is an in-room toggle, not a
    // second engine.
    add(KiteEngine(IosKiteMediaResolver))
}

actual fun RoomViewmodel.instantiateNetworkManager(): NetworkManager {
    val preferredEngine = NETWORK_ENGINE.value()
    return when (preferredEngine) {
        // Swift registers the factory at startup, and swiftnio is the iOS default. A build whose
        // bridge never registered must not crash on the way into every room. Ktor is a weaker
        // engine (no TLS upgrade), not a broken one, so fall back to it and log that.
        "swiftnio" -> instantiateSwiftNioNetworkManager?.invoke(this) ?: run {
            loggy("SwiftNIO bridge is not registered; falling back to the Ktor transport.")
            KtorNetworkManager(this)
        }
        else -> KtorNetworkManager(this)
    }
}

actual fun generateTimestampMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000.0).roundToLong()
}

/**
 * True when the device uses a 24-hour clock. iOS states this through the locale's hour template:
 * the format it returns for "j" (the locale's preferred hour field) contains an "a" only on a
 * 12-hour clock. This also follows the Settings switch, because that switch changes the current
 * locale's format.
 */
actual fun deviceUses24HourClock(): Boolean {
    val format = NSDateFormatter.dateFormatFromTemplate("j", 0uL, NSLocale.currentLocale)
    return format?.contains("a") != true
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

/** The major version of the system this is running on, read once. */
private val iosMajorVersion: Int by lazy {
    UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull() ?: 0
}

/**
 * Applies an orientation mask. It updates the delegate's `supportedInterfaceOrientationsForWindow`
 * answer, requests the new geometry, and calls `setNeedsUpdateOfSupportedInterfaceOrientations()`
 * on the root view controller.
 *
 * That last call is required. iOS 16 and later cache the last supported-orientations answer and
 * do not ask the delegate again. A geometry request that names a concrete orientation (such as
 * landscape) takes effect, but an "All" mask names no target orientation. Without the call, the
 * stale cached mask keeps rotation locked after the user leaves a room (a group of people
 * watching together).
 *
 * Everything after the delegate needs iOS 16 or later. `UIWindowSceneGeometryPreferencesIOS` is
 * constructed, not only called, so `respondsToSelector` cannot guard it, and on iOS 15 the class
 * does not exist. The app supports iOS 15, and this runs on Home as well as in the room, so the
 * version check comes first. On iOS 15 the delegate answer is all there is, and it takes effect
 * at the next rotation.
 */
private fun applyOrientationMask(mask: UIInterfaceOrientationMask) {
    delegato.myOrientationMask = mask

    if (iosMajorVersion < 16) {
        /* The delegate answer above is the whole fix here. It applies at the next rotation or
         * view-controller transition, not at once. Forcing it sooner needs
         * UIViewController.attemptRotationToDeviceOrientation, a class method that Kotlin's
         * UIKit bindings do not expose, so it would have to come from the Swift side. A bridge
         * is not worth it for one old system, and a late rotation is far better than a crash. */
        return
    }

    val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull() as? UIWindowScene
    scene?.requestGeometryUpdateWithPreferences(
        geometryPreferences = UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = mask),
        errorHandler = null
    )
    // Not in Kotlin's UIKit bindings: invoke the selector dynamically.
    val rootVc = (scene?.windows?.firstOrNull() as? UIWindow)?.rootViewController
    val needsUpdate = NSSelectorFromString("setNeedsUpdateOfSupportedInterfaceOrientations")
    if (rootVc?.respondsToSelector(needsUpdate) == true) {
        rootVc.performSelector(needsUpdate)
    }
}

@Composable
actual fun EnterRoomMode(portrait: Boolean) {
    LaunchedEffect(portrait) {
        // Set it again on every room entry, even when the app never left the foreground.
        UIApplication.sharedApplication.idleTimerDisabled = true
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

/**
 * Returns the log folder, under Library, where the Files app cannot reach it. Documents is shared
 * on purpose for media, and a week of protocol logs (usernames, room names, file names) does not
 * belong there. A Documents/logs folder from an older version is removed.
 */
actual fun getLogDirectoryPath(): String? {
    return try {
        val fm = NSFileManager.defaultManager
        val library = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, true).firstOrNull() as? String ?: return null
        val logDir = "$library/Logs/Synkplay"
        if (!fm.fileExistsAtPath(logDir)) {
            fm.createDirectoryAtPath(logDir, withIntermediateDirectories = true, attributes = null, error = null)
        }
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String)?.let { docs ->
            val legacy = "$docs/logs"
            if (fm.fileExistsAtPath(legacy)) fm.removeItemAtPath(legacy, null)
        }
        logDir
    } catch (_: Exception) {
        null
    }
}

actual fun platformDescription(): String {
    val device = UIDevice.currentDevice
    return "${device.systemName} ${device.systemVersion} (${device.model})"
}

actual fun getCacheDirectoryPath(subdir: String): String? {
    return try {
        val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        val cacheDir = paths.firstOrNull() as? String ?: return null
        val dir = "$cacheDir/$subdir"
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(dir)) {
            fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        }
        dir
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
 * Always null on iOS: no engine here runs mpv, so there is no config dir to point at. The
 * mpv.conf import/export prefs and the libass subfont install both check for null and skip.
 */
actual fun getMpvConfFilePath(): String? = null

actual fun consumePendingShortcut(): app.home.JoinConfig? {
    return app.pendingShortcutJoinConfig.value?.also {
        app.pendingShortcutJoinConfig.value = null
    }
}

actual fun reducedMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()

@Composable
actual fun rememberScreenReaderActive(): State<Boolean> {
    val active = remember { mutableStateOf(UIAccessibilityIsVoiceOverRunning()) }
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIAccessibilityVoiceOverStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> active.value = UIAccessibilityIsVoiceOverRunning() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
    return active
}

/** Always false: there is no TV build for iOS (tvOS would be a separate target). */
actual fun isTelevision(): Boolean = false

actual fun localizedLanguageName(iso6391: String, inLanguage: String): String? {
    val displayIn = NSLocale(localeIdentifier = inLanguage)
    val name: String? = displayIn.displayNameForKey(NSLocaleLanguageCode, iso6391)
    // Foundation returns the code unchanged when it has no name for the language.
    if (name.isNullOrBlank() || name.lowercase() == iso6391.lowercase()) return null
    return name.replaceFirstChar { c -> c.uppercaseChar() }
}
