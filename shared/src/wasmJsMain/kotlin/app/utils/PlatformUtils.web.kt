package app.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.ClipEntry
import app.home.JoinConfig
import app.player.PlayerEngine
import app.player.web.webVideoEngine
import app.protocol.network.NetworkManager
import app.protocol.network.WebSocketNetworkManager
import app.room.RoomViewmodel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import kotlinx.browser.window
import kotlin.time.Clock
import SyncplayMobile.shared.KiteBuildConfig

actual val platform: Platform = Platform.Web

/**
 * The browser's own fetch(), behind the Ktor client that the rest of the app already uses.
 *
 * Two things differ from every other platform, and this code cannot change either one. The
 * browser decides the connect and socket timeouts, so only the request timeout is set. Scripts
 * may not set the User-Agent header, so requests carry the browser's own; a server that needs to
 * know that Synkplay is calling must learn it some other way.
 */
actual val httpClient: HttpClient by lazy {
    HttpClient(Js) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
        install(Logging) {
            logger = KtorLoggyLogger
            level = if (KiteBuildConfig.IS_DEBUG) LogLevel.ALL else LogLevel.INFO
            sanitizeHeader { header -> header == "Api-Key" || header == HttpHeaders.Authorization }
            filter { request -> request.url.host.startsWith("api.") }
        }
    }
}

/**
 * One engine (video player): the browser's own `<video>`.
 *
 * The other engines are native: ExoPlayer and mpv on Android, AVPlayer and VLCKit on iOS, and
 * KitePlayer through FFmpeg over JNI and cinterop. None of them compiles for the browser.
 */
actual val availablePlatformPlayerEngines: List<PlayerEngine> = listOf(webVideoEngine)

/**
 * Always the WebSocket transport. A page cannot open a TCP socket, so the preference that picks
 * between Netty, SwiftNIO and Ktor has nothing to choose from on this platform.
 */
actual fun RoomViewmodel.instantiateNetworkManager(): NetworkManager = WebSocketNetworkManager(this)

actual fun generateTimestampMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** No browser reports a TV. To the page, a TV browser looks like a desktop browser. */
actual fun isTelevision(): Boolean = false

/** Follows the hour format of the page's own locale. */
actual fun deviceUses24HourClock(): Boolean = runCatching { !jsPrefersTwelveHourClock() }.getOrDefault(true)

private fun jsPrefersTwelveHourClock(): Boolean =
    js("(new Intl.DateTimeFormat().resolvedOptions().hour12 === true)")

/**
 * A strong reference behind the weak-reference API.
 *
 * JavaScript has a real `WeakRef`, but Kotlin/Wasm does not expose it. So on the web, the app
 * viewmodel's roomWeakRef keeps the last RoomViewmodel reachable after its room closes, until
 * the next room replaces it.
 */
actual class WeakRef<T : Any>(internal val target: T)

actual fun <T : Any> createWeakRef(obj: T): WeakRef<T> = WeakRef(obj)

actual fun <T : Any> WeakRef<T>?.get(): T? = this?.target

/* ---- files: a page has no filesystem ------------------------------------------------------ */

actual fun getFileName(uri: PlatformFile): String? = runCatching { uri.name.takeIf { it.isNotBlank() } }.getOrNull()

actual fun getFolderName(uri: String): String? =
    uri.trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }

/** Reading a picked file's size is a suspend call on this platform, and this contract is not. */
actual fun getFileSize(uri: PlatformFile): Long? = null

actual fun getLogDirectoryPath(): String? = null

actual fun getCacheDirectoryPath(subdir: String): String? = null

actual fun appendToFile(path: String, content: String) = Unit

actual fun writeTextFile(path: String, content: String) = Unit

actual fun listFiles(directoryPath: String): List<String> = emptyList()

actual fun readFile(path: String): String = ""

actual fun deleteFile(path: String) = Unit

actual fun writeFileBytes(path: String, bytes: ByteArray) = Unit

actual fun readFileBytes(path: String): ByteArray? = null

actual fun fileExists(path: String): Boolean = false

/** Always null: only Android has the mpv engine. */
actual fun getMpvConfFilePath(): String? = null

/* ---- the page as an environment ----------------------------------------------------------- */

/**
 * In a browser, clipboard reads are asynchronous and need permission, and this contract is
 * neither. So paste from the clipboard is unavailable instead of wrong.
 */
actual fun ClipEntry.getText(): String? = null

/**
 * Does nothing.
 *
 * In a browser, both parts of the room's window policy need a user gesture: fullscreen must be
 * requested from a click, and the orientation lock applies only in fullscreen. So this belongs on
 * the room's own fullscreen control, not in an effect that runs on entry and fails.
 */
@Composable
actual fun EnterRoomMode(portrait: Boolean) = Unit

@Composable
actual fun ExitRoomMode() = Unit

/** A page never learns the machine's LAN address, and needs none: it cannot host a server. */
actual fun getDeviceIpAddress(): String? = null

actual fun platformDescription(): String = runCatching { window.navigator.userAgent }.getOrDefault("Web")

/** Honours the OS-level "reduce motion" switch, which browsers expose as a media query. */
actual fun reducedMotion(): Boolean =
    runCatching { window.matchMedia("(prefers-reduced-motion: reduce)").matches }.getOrDefault(false)

/** A browser does not tell a page whether a screen reader runs. */
actual val hiddenPointerIcon: PointerIcon? = null

@Composable
actual fun rememberScreenReaderActive(): State<Boolean> = remember { mutableStateOf(false) }

/**
 * A join request passed in the address bar. It is the web version of a launcher shortcut and of
 * the `synkplay://` invite link:
 *
 *     https://…/?user=Alice&room=movienight&server=syncplay.pl&port=8997&pw=secret
 *
 * It is read once and then cleared, like every other platform's shortcut.
 */
actual fun consumePendingShortcut(): JoinConfig? {
    val query = runCatching { window.location.search }.getOrNull() ?: return null
    if (query.length <= 1) return null

    val params = query.removePrefix("?").split("&").mapNotNull { pair ->
        val key = pair.substringBefore('=', "")
        val raw = pair.substringAfter('=', "")
        if (key.isBlank()) null else key to decodeUriComponent(raw)
    }.toMap()

    val user = params["user"]?.takeIf { it.isNotBlank() } ?: return null
    val room = params["room"]?.takeIf { it.isNotBlank() } ?: return null

    var config = JoinConfig(user = user, room = room)
    params["server"]?.takeIf { it.isNotBlank() }?.let { config = config.copy(ip = it) }
    params["port"]?.toIntOrNull()?.let { config = config.copy(port = it) }
    params["pw"]?.let { config = config.copy(pw = it) }

    // Clear it, so a reload is a plain visit and the credentials leave the address bar.
    runCatching { window.history.replaceState(null, "", window.location.pathname) }
    return config
}

private fun decodeUriComponent(value: String): String =
    runCatching { jsDecodeUriComponent(value.replace('+', ' ')) }.getOrDefault(value)

private fun jsDecodeUriComponent(value: String): String = js("decodeURIComponent(value)")

/**
 * The browser's name for a language, in the language that the app displays.
 *
 * `Intl.DisplayNames` is the browser's own name table, so this needs no bundled data. An empty
 * answer, or one that only repeats the code, becomes null, and the caller falls back to English.
 */
actual fun localizedLanguageName(iso6391: String, inLanguage: String): String? =
    runCatching { jsLanguageName(iso6391, inLanguage).takeIf { it.isNotBlank() && !it.equals(iso6391, true) } }
        .getOrNull()

private fun jsLanguageName(code: String, inLanguage: String): String =
    js("(function(){try{return new Intl.DisplayNames([inLanguage],{type:'language'}).of(code)||''}catch(e){return ''}})()")
