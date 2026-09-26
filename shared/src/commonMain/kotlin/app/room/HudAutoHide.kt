package app.room

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * The inputs of [autoHideHud]. The HUD is the set of controls drawn over the video.
 * Every change restarts the idle timer. [activity] changes on each touch, key press or mouse move,
 * so an interaction restarts the timer even when playback stays the same. [held] is true while
 * something keeps the controls open: a panel, a menu, the keyboard, or a mouse pointer that rests
 * on a control. [screenReader] is true while a screen reader runs. Its gestures send no presses,
 * so the timer would hide the controls while the person is still reading them.
 */
internal data class HudAutoHideState(
    val idleSeconds: Int,
    val hudVisible: Boolean,
    val hasVideo: Boolean,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val held: Boolean,
    val activity: Long,
    val screenReader: Boolean = false,
) {
    val playbackActive: Boolean get() = hasVideo && isPlaying && !isBuffering

    /** The mouse pointer hides with controls that are hidden during playback, and only then. */
    val hidesPointer: Boolean get() = !hudVisible && playbackActive && !held && !screenReader
}

/**
 * Hides the HUD after [HudAutoHideState.idleSeconds] of uninterrupted playback. When playback
 * pauses or buffers, or a screen reader starts, controls that this timer hid come back. Controls
 * that the user hid with a tap on the background stay hidden until the user shows them again.
 */
internal suspend fun autoHideHud(
    states: Flow<HudAutoHideState>,
    setHudVisible: (Boolean) -> Unit,
) {
    var automaticallyHidden = false
    states.collectLatest { state ->
        if (state.hudVisible) automaticallyHidden = false
        if (!state.playbackActive || state.screenReader) {
            if (automaticallyHidden) {
                automaticallyHidden = false
                setHudVisible(true)
            }
            return@collectLatest
        }
        if (state.idleSeconds <= 0 || !state.hudVisible || state.held) return@collectLatest
        delay(state.idleSeconds * 1000L)
        automaticallyHidden = true
        setHudVisible(false)
    }
}
