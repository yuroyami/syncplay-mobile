package app.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.AdamScreen
import app.Screen
import app.SyncplayViewmodel
import app.player.Playback
import app.preferences.Preferences
import app.preferences.set
import app.preferences.value
import app.protocol.WireMessage
import app.utils.initializeDatastore
import app.utils.loggy
import app.utils.platformCallback
import app.uicomponents.controls.TextInputFocus
import app.preferences.warmPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.Toolkit
import kotlin.math.roundToInt

/** Global viewmodel handle, mirroring the Android Activity / iOS controller pattern. */
var globalViewmodel: SyncplayViewmodel? by mutableStateOf(null)

/** Process-lifetime scope for fire-and-forget UI work (keyboard shortcuts). */
private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

/** Where the volume was before M muted it, so M again brings it back. */
private var mutedFrom: Int? = null

fun main(args: Array<String>) {
    initializeDatastore()
    warmPreferences()
    // Before anything composes: Compose Desktop resolves its strings against the JVM locale.
    applyDisplayLanguage(runCatching { Preferences.DISPLAY_LANG.value() }.getOrDefault(""))
    // The trace reaches the log file before the JVM's own handler prints and exits.
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        loggy("Uncaught exception on ${thread.name}: ${throwable.stackTraceToString()}")
        previousHandler?.uncaughtException(thread, throwable)
    }
    platformCallback = DesktopPlatformCallback
    parseJoinArgs(args)

    application {
        val restored = restoreWindow()
        val windowState = rememberWindowState(placement = restored.placement, position = restored.position, size = restored.size)

        Window(
            onCloseRequest = ::exitApplication,
            title = "Synkplay",
            state = windowState,
            // Arrows are claimed before focus dispatch, or Compose spends them moving focus and the
            // room never sees them. Everything else stays after dispatch, so a space typed into
            // chat is a space. Nothing is claimed while a text field has focus.
            onPreviewKeyEvent = { event -> handleArrowKey(event) },
            onKeyEvent = { event -> handleGlobalKey(event, windowState) },
        ) {
            // A 320dp side dock beside a 16:9 picture that is 480dp tall.
            LaunchedEffect(Unit) { window.minimumSize = Dimension(800, 480) }

            // Size, position and placement are remembered; the write waits for the drag to settle.
            LaunchedEffect(windowState.size, windowState.position, windowState.placement) {
                delay(400)
                saveWindow(windowState)
            }

            // Leaving the room always returns to the floating placement, or home inherits fullscreen.
            val vm = globalViewmodel
            LaunchedEffect(vm) {
                vm ?: return@LaunchedEffect
                snapshotFlow { vm.backstack.lastOrNull() is Screen.Room }.collect { inRoom ->
                    if (!inRoom && windowState.placement == WindowPlacement.Fullscreen) windowState.placement = WindowPlacement.Floating
                }
            }

            AdamScreen(onGlobalViewmodel = { globalViewmodel = it })
        }
    }
}

private class RestoredWindow(val placement: WindowPlacement, val position: WindowPosition, val size: DpSize)

/** The saved window, clamped to the display that exists now; fullscreen is never restored. */
private fun restoreWindow(): RestoredWindow {
    val fallback = RestoredWindow(WindowPlacement.Floating, WindowPosition.PlatformDefault, DpSize(1280.dp, 800.dp))
    val parts = Preferences.DESKTOP_WINDOW.value().split(",")
    if (parts.size < 5) return fallback
    val x = parts[0].toIntOrNull() ?: return fallback
    val y = parts[1].toIntOrNull() ?: return fallback
    val w = parts[2].toIntOrNull() ?: return fallback
    val h = parts[3].toIntOrNull() ?: return fallback
    val screen = runCatching { Toolkit.getDefaultToolkit().screenSize }.getOrNull()
    val width = (if (screen != null) w.coerceAtMost(screen.width) else w).coerceAtLeast(800)
    val height = (if (screen != null) h.coerceAtMost(screen.height) else h).coerceAtLeast(480)
    val left = if (screen != null) x.coerceIn(0, (screen.width - width).coerceAtLeast(0)) else x
    val top = if (screen != null) y.coerceIn(0, (screen.height - height).coerceAtLeast(0)) else y
    val placement = if (parts[4] == "Maximized") WindowPlacement.Maximized else WindowPlacement.Floating
    return RestoredWindow(placement, WindowPosition.Absolute(left.dp, top.dp), DpSize(width.dp, height.dp))
}

private suspend fun saveWindow(state: WindowState) {
    if (state.placement == WindowPlacement.Fullscreen) return
    val position = state.position
    val x = if (position is WindowPosition.Absolute) position.x.value.roundToInt() else return
    val y = position.y.value.roundToInt()
    val record = listOf(x, y, state.size.width.value.roundToInt(), state.size.height.value.roundToInt(), state.placement.name).joinToString(",")
    Preferences.DESKTOP_WINDOW.set(record)
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
                delay(500)
                vm = globalViewmodel?.roomWeakRef?.get()
            }
            vm.player.injectVideoURL(url)

            if (autoplay) {
                delay(8000)
                vm.dispatcher.controlPlayback(Playback.PLAY, true)
            }
        }
    }
}

/**
 * The arrows: seek on the horizontal pair, volume on the vertical one, claimed before Compose's
 * focus traversal gets them. They act only inside the room, and never while someone is typing.
 */
private fun handleArrowKey(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (TextInputFocus.isTyping) return false
    val global = globalViewmodel ?: return false
    if (global.backstack.lastOrNull() !is Screen.Room) return false
    val vm = global.roomWeakRef?.get() ?: return false
    if (!vm.playerManager.hasVideo.value) return false

    val times = if (event.isShiftPressed) 5 else 1
    val volume = vm.player.volume
    val volumeStep = 5
    return when (event.key) {
        Key.DirectionLeft -> { vm.dispatcher.seekBy(-Preferences.SEEK_BACKWARD_JUMP.value() * times); true }
        Key.DirectionRight -> { vm.dispatcher.seekBy(Preferences.SEEK_FORWARD_JUMP.value() * times); true }
        Key.DirectionUp -> { volume.set(volume.current() + volumeStep); true }
        Key.DirectionDown -> { volume.set(volume.current() - volumeStep); true }
        else -> false
    }
}

/**
 * The window-level key map, run after focus dispatch (onKeyEvent, not onPreviewKeyEvent) so a
 * space typed into the chat field never toggles playback. Escape is the one key that acts
 * anywhere: leave fullscreen, else close the open panels, else hide the HUD, else pop a page.
 */
private fun handleGlobalKey(event: KeyEvent, windowState: WindowState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val global = globalViewmodel ?: return false
    val vm = global.roomWeakRef?.get()
    val inRoom = global.backstack.lastOrNull() is Screen.Room && vm != null

    if ((event.isMetaPressed || event.isCtrlPressed) && event.key == Key.Comma) {
        global.backstack.add(Screen.Settings())
        return true
    }

    if (event.key == Key.Escape) {
        if (windowState.placement == WindowPlacement.Fullscreen) {
            windowState.placement = WindowPlacement.Floating
            return true
        }
        if (inRoom && vm != null) {
            val ui = vm.uiState
            val panelOpen = ui.tabCardUserInfo.value || ui.tabCardSharedPlaylist.value || ui.tabCardRoomPreferences.value || ui.controlPanel.value
            when {
                panelOpen -> {
                    ui.toggleUserInfo(false)
                    ui.toggleSharedPlaylist(false)
                    ui.toggleRoomPreferences(false)
                    ui.controlPanel.value = false
                }
                ui.visibleHUD.value -> ui.visibleHUD.value = false
                else -> ui.askLeave.value = true
            }
            return true
        }
        if (global.backstack.size > 1) {
            global.backstack.removeAt(global.backstack.lastIndex)
            return true
        }
        return false
    }

    if (!inRoom || vm == null) return false

    when (event.key) {
        Key.F -> {
            windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
            return true
        }
        Key.C -> {
            vm.uiState.showHud()
            runCatching { vm.uiState.chatFocus.requestFocus() }
            return true
        }
        Key.R -> {
            if (vm.isSoloMode) return false
            val next = !vm.session.ready.value
            vm.session.ready.value = next
            vm.networkManager.sendAsync(WireMessage.readiness(isReady = next, manuallyInitiated = true))
            return true
        }
        else -> Unit
    }

    if (!vm.playerManager.hasVideo.value) return false
    val times = if (event.isShiftPressed) 5 else 1
    val volume = vm.player.volume
    val volumeStep = 5

    return when (event.key) {
        Key.Spacebar -> {
            // The app's own intent, never a live engine probe (a probe mid-buffer says "not playing").
            vm.dispatcher.controlPlayback(if (vm.protocol.expectedPlaying) Playback.PAUSE else Playback.PLAY, true)
            true
        }
        // The arrows are handled before focus dispatch, in handleArrowKey.
        Key.M -> {
            val current = volume.current()
            val restore = mutedFrom
            if (current == 0 && restore != null) {
                volume.set(restore)
                mutedFrom = null
            } else {
                mutedFrom = current
                volume.set(0)
            }
            true
        }
        else -> false
    }
}
