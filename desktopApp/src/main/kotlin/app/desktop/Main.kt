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
import app.uicomponents.controls.ArrowKeyFocus
import app.preferences.warmPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Dimension
import java.awt.Toolkit
import kotlin.math.roundToInt

/** Global viewmodel handle, as in the Android Activity and the iOS controller. */
var globalViewmodel: SyncplayViewmodel? by mutableStateOf(null)

/** Process-lifetime scope for fire-and-forget UI work (the --media auto-load). */
private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

/** How long --media waits for a room's player before giving up. */
private const val MEDIA_WAIT_TIMEOUT_MS = 120_000L

/** Where the volume was before M muted it, so M again brings it back. */
private var mutedFrom: Int? = null

fun main(args: Array<String>) {
    initializeDatastore()
    warmPreferences()
    // Write the trace to the log file before the previous handler prints it and exits.
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
            // The arrow keys are handled before focus dispatch, or Compose uses them to move focus
            // and the room never sees them. All other keys are handled after dispatch, so a space
            // typed into chat stays a space. No arrow key is taken while a text field, a slider or
            // a stepper has focus.
            onPreviewKeyEvent = { event -> handleArrowKey(event) },
            onKeyEvent = { event -> handleGlobalKey(event, windowState) },
        ) {
            // 800 wide fits the 320dp side panel next to a 16:9 picture that is 480dp wide.
            LaunchedEffect(Unit) { window.minimumSize = Dimension(800, 480) }

            // The window remembers its size, position and placement. The save waits 400 ms, so a
            // drag is saved once, when it stops.
            LaunchedEffect(windowState.size, windowState.position, windowState.placement) {
                delay(400)
                saveWindow(windowState)
            }

            // Leaving the room returns the window to the floating placement, so the home screen
            // does not open in fullscreen.
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
 * Command-line auto-join, the desktop version of the mobile shortcuts:
 *   synkplay --user Alice --room movienight [--host syncplay.pl] [--port 8997] [--pw secret]
 * Only --user and --room are required; host and port fall back to the JoinConfig defaults.
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

    // Optional: load a media URL once the room's player engine is ready (for scripted tests and
    // for "synkplay --room X --media http://..."). --autoplay also starts playback 8 s later.
    value("--media")?.let { url ->
        val autoplay = args.contains("--autoplay")
        mainScope.launch {
            /* The wait has a time limit. This runs on the main dispatcher and mainScope is never
             * cancelled. Without the limit, a launch that never reaches a room keeps a
             * twice-a-second poll on the UI thread for the life of the app. */
            val vm = withTimeoutOrNull(MEDIA_WAIT_TIMEOUT_MS) {
                var found = globalViewmodel?.roomWeakRef?.get()
                while (found == null || !found.playerManager.isPlayerReady.value) {
                    delay(500)
                    found = globalViewmodel?.roomWeakRef?.get()
                }
                found
            }
            if (vm == null) {
                loggy("--media: no room player appeared within ${MEDIA_WAIT_TIMEOUT_MS / 1000}s; giving up.")
                return@launch
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
 * The arrow keys: left and right seek, up and down change the volume. They are handled before
 * Compose's focus traversal gets them. They act only in a room with a video, and never while a
 * control that uses the arrow keys (a text field, a slider or a stepper) has focus.
 */
private fun handleArrowKey(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (ArrowKeyFocus.isClaimed) return false
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
 * The window-level key map. It runs after focus dispatch (onKeyEvent, not onPreviewKeyEvent), so
 * a space typed into the chat field never toggles playback. Ctrl or Cmd plus comma opens the
 * settings from any screen. Escape first leaves fullscreen. Then, in the room, it closes the
 * open panels, else hides the HUD (the controls over the video), else asks to leave the room.
 * Outside the room it goes back one page.
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
            // anySidePanelOpen covers every side panel. A hand-picked list misses panels such as
            // Tracks or Gestures, and Escape then hides the HUD behind the open panel.
            val panelOpen = ui.anySidePanelOpen || ui.controlPanel.value
            when {
                panelOpen -> {
                    ui.closeSidePanels()
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
            // Toggle from the app's own intent, never from a live engine probe: a probe during
            // buffering says "not playing".
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
