package app.utils

import androidx.compose.runtime.Composable
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
 * The browser's own fetch(), behind the Ktor client the rest of the app already talks to.
 *
 * Two things differ from every other platform and neither is fixable here. Connect and socket
 * timeouts are the browser's to decide, so only the request timeout is set. And the User-Agent
 * header is forbidden to scripts, so requests carry the browser's own; anything that wants to
 * know it is Synkplay calling has to be told some other way.
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
 * One engine, the browser's own `<video>`.
 *
 * The other four are native: ExoPlayer and mpv are Android, VLCKit is iOS, and KitePlayer decodes
 * through FFmpeg over JNI and cinterop. None of them has anything to compile to here.
 */
actual val availablePlatformPlayerEngines: List<PlayerEngine> = listOf(webVideoEngine)

/**
 * Always the WebSocket transport. A page cannot open a TCP socket, so the preference that picks
 * between Netty, SwiftNIO and Ktor has nothing to choose from on this platform.
 */
actual fun RoomViewmodel.instantiateNetworkManager(): NetworkManager = WebSocketNetworkManager(this)

actual fun generateTimestampMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** No browser reports a TV, and a TV browser is a desktop browser as far as the page can tell. */
actual fun isTelevision(): Boolean = false

/** What the page's own locale formats an hour as. */
actual fun deviceUses24HourClock(): Boolean = runCatching { !jsPrefersTwelveHourClock() }.getOrDefault(true)

private fun jsPrefersTwelveHourClock(): Boolean =
    js("(new Intl.DateTimeFormat().resolvedOptions().hour12 === true)")

/**
 * A strong reference wearing a weak reference's name.
 *
 * JavaScript does have a real `WeakRef`, but it is not surfaced to Kotlin/Wasm, and the two
 * holders of one here (the room from the app viewmodel, the viewmodel from the platform) are
 * both cleared explicitly on teardown, so nothing leaks for the life of a tab. Worth revisiting
 * if a third holder appears.
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

/** mpv is Android's engine alone. */
actual fun getMpvConfFilePath(): String? = null

/* ---- the page as an environment ----------------------------------------------------------- */

/**
 * Clipboard reads are asynchronous and permission-gated in a browser, and this contract is
 * neither, so paste-from-clipboard is unavailable rather than wrong.
 */
actual fun ClipEntry.getText(): String? = null

/**
 * Nothing yet.
 *
 * Both halves of the room's window policy are gated on a user gesture here: fullscreen must be
 * requested from a click, and the orientation lock only applies once fullscreen is held. That
 * makes this a job for the room's own fullscreen control rather than an effect that fires on
 * entry, which is why this is empty instead of trying and failing.
 */
@Composable
actual fun EnterRoomMode(portrait: Boolean) = Unit

@Composable
actual fun ExitRoomMode() = Unit

/** A page never learns the machine's LAN address, and would have no use for it: it cannot host. */
actual fun getDeviceIpAddress(): String? = null

actual fun platformDescription(): String = runCatching { window.navigator.userAgent }.getOrDefault("Web")

/** Honours the OS-level "reduce motion" switch, which browsers expose as a media query. */
actual fun reducedMotion(): Boolean =
    runCatching { window.matchMedia("(prefers-reduced-motion: reduce)").matches }.getOrDefault(false)

/**
 * A join handed over in the address bar, which is this platform's answer to a launcher shortcut
 * and to the `synkplay://` invite link:
 *
 *     https://…/?user=Alice&room=movienight&server=syncplay.pl&port=8997&pw=secret
 *
 * Read once and cleared, same as every other platform's shortcut.
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

    // Cleared so a reload is a plain visit, and so the credentials stop sitting in the address bar.
    runCatching { window.history.replaceState(null, "", window.location.pathname) }
    return config
}

private fun decodeUriComponent(value: String): String =
    runCatching { jsDecodeUriComponent(value.replace('+', ' ')) }.getOrDefault(value)

private fun jsDecodeUriComponent(value: String): String = js("decodeURIComponent(value)")

/**
 * What the browser calls a language, in the language the app is displaying.
 *
 * `Intl.DisplayNames` is the browser's own name table, so this needs no bundled data. An empty
 * answer becomes null and the caller falls back to English.
 */
actual fun localizedLanguageName(iso6391: String, inLanguage: String): String? =
    runCatching { jsLanguageName(iso6391, inLanguage).takeIf { it.isNotBlank() && !it.equals(iso6391, true) } }
        .getOrNull()

private fun jsLanguageName(code: String, inLanguage: String): String =
    js("(function(){try{return new Intl.DisplayNames([inLanguage],{type:'language'}).of(code)||''}catch(e){return ''}})()")
