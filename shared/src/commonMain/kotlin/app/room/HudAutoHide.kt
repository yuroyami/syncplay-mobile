package app.room

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * The inputs of [autoHideHud]. The HUD is the set of controls drawn over the video.
 * Every change restarts the idle timer. [activity] changes on each touch or key press, so an
 * interaction restarts the timer even when playback stays the same.
 */
internal data class HudAutoHideState(
    val idleSeconds: Int,
    val hudVisible: Boolean,
    val hasVideo: Boolean,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val held: Boolean,
    val activity: Long,
) {
    val playbackActive: Boolean get() = hasVideo && isPlaying && !isBuffering
}

/**
 * Hides the HUD after [HudAutoHideState.idleSeconds] of uninterrupted playback. When playback
 * pauses or buffers, controls that this timer hid come back. Controls that the user hid with a
 * tap on the background stay hidden until the user shows them again.
 */
internal suspend fun autoHideHud(
    states: Flow<HudAutoHideState>,
    setHudVisible: (Boolean) -> Unit,
) {
    var automaticallyHidden = false
    states.collectLatest { state ->
        if (state.hudVisible) automaticallyHidden = false
        if (!state.playbackActive) {
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
