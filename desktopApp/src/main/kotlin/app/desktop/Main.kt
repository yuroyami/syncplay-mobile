package app.desktop

import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.AdamScreen
import app.SyncplayViewmodel
import app.player.Playback
import app.preferences.set
import app.utils.initializeDatastore
import app.utils.platformCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Global viewmodel handle, mirroring the Android Activity / iOS controller pattern. */
var globalViewmodel: SyncplayViewmodel? = null

/** Process-lifetime scope for fire-and-forget UI work (keyboard shortcuts). */
private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

fun main(args: Array<String>) {
    initializeDatastore()
    platformCallback = DesktopPlatformCallback

    // --engine is gone with the engines it switched between. Desktop runs KitePlayer and only
    // KitePlayer, so there is nothing for a launch flag to choose.

    parseJoinArgs(args)

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Synkplay",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
            onKeyEvent = { event -> handleGlobalKey(event.type == KeyEventType.KeyDown, event.key) },
        ) {
            AdamScreen(
                onGlobalViewmodel = { globalViewmodel = it }
            )
        }
    }
}

/**
 * Command-line auto-join, the desktop analog of mobile shortcuts:
 *   synkplay --user Alice --room movienight [--host syncplay.pl] [--port 8997] [--pw secret]
 * Only user+room are required; host/port fall back to the JoinConfig defaults.
 */
private fun parseJoinArgs(args: Array<String>) {
    fun value(flag: String): String? =
        args.toList().zipWithNext().firstOrNull { it.first == flag }?.second

    val user = value("--user") ?: return
    val room = value("--room") ?: return
    var config = app.home.JoinConfig(user = user, room = room)
    value("--host")?.let { config = config.copy(ip = it) }
    value("--port")?.toIntOrNull()?.let { config = config.copy(port = it) }
    value("--pw")?.let { config = config.copy(pw = it) }
    app.utils.pendingDesktopJoin = config

    // Optional: load a media URL once the room's player engine is up (useful for scripted
    // testing and "synkplay --room X --media http://..." power users).
    value("--media")?.let { url ->
        val autoplay = args.contains("--autoplay")
        mainScope.launch {
            var vm = globalViewmodel?.roomWeakRef?.get()
            while (vm == null || !vm.playerManager.isPlayerReady.value) {
                kotlinx.coroutines.delay(500)
                vm = globalViewmodel?.roomWeakRef?.get()
            }
            vm.player.injectVideoURL(url)

            if (autoplay) {
                kotlinx.coroutines.delay(8000)
                vm.dispatcher.controlPlayback(Playback.PLAY, true)
            }
        }
    }
}

/**
 * Window-level media keys. This runs AFTER focus dispatch (onKeyEvent, not onPreviewKeyEvent),
 * so typing a space into the chat field never toggles playback — only unconsumed keys land here.
 * Mirrors the Android TV key handling: space/enter toggle, left/right seek.
 */
private fun handleGlobalKey(isDown: Boolean, key: Key): Boolean {
    if (!isDown) return false
    val vm = globalViewmodel?.roomWeakRef?.get() ?: return false
    if (!vm.playerManager.hasVideo.value) return false

    return when (key) {
        Key.Spacebar -> {
            mainScope.launch {
                val isPlaying = vm.player.isPlaying()
                vm.dispatcher.controlPlayback(if (isPlaying) Playback.PAUSE else Playback.PLAY, true)
            }
            true
        }

        Key.DirectionLeft -> {
            vm.dispatcher.seekBckwd()
            true
        }

        Key.DirectionRight -> {
            vm.dispatcher.seekFrwrd()
            true
        }

        else -> false
    }
}
