package app.room

import app.AbstractManager
import app.player.Playback
import app.preferences.Preferences
import app.preferences.value
import app.protocol.sync.AUTOPLAY_COUNTDOWN_SECONDS
import app.protocol.sync.AutoplayState
import app.protocol.sync.ReadinessSummary
import app.protocol.sync.shouldCountDown
import app.protocol.sync.summariseReadiness
import app.utils.loggy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Who the room is waiting for, and the countdown once it is not waiting for anyone.
 *
 * The gate that blocks an unpause has always been here. What was missing is everything around
 * it: the room never said whose readiness it was waiting on, and it never started on its own
 * when everyone arrived, which the desktop client has always done.
 *
 * The decisions live in [app.protocol.sync.Readiness] and are tested there. This part only
 * watches, times and acts.
 */
class ReadinessManager(private val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    /** What the room should show about readiness right now. */
    val state: StateFlow<AutoplayState>
        field = MutableStateFlow<AutoplayState>(AutoplayState.Idle)

    /** Who the room is waiting for, if anyone. */
    val summary: StateFlow<ReadinessSummary>
        field = MutableStateFlow(ReadinessSummary(emptyList(), emptyList()))

    private var countdown: Job? = null

    /** Watches the roster, our own readiness and the room's pause state. */
    fun start() {
        if (viewmodel.isSoloMode) return
        onIOThread {
            viewmodel.session.userList.collect { evaluate() }
        }
    }

    /**
     * Re-reads the room. Called on every roster change and after anything that could move the
     * readiness picture: our own ready toggle, a pause, a play.
     */
    fun evaluate() {
        if (viewmodel.isSoloMode) return
        val session = viewmodel.session
        val room = summariseReadiness(session.userList.value, session.currentUsername)
        summary.value = room

        val counting = shouldCountDown(
            autoplayEnabled = Preferences.AUTOPLAY.value(),
            roomPaused = viewmodel.protocol.globalPaused,
            canControlRoom = !session.isInControlledRoomWithoutController(),
            selfReady = session.ready.value,
            summary = room,
        )

        if (!counting) {
            cancelCountdown()
            state.value = when {
                room.alone || room.everyoneReady -> AutoplayState.Idle
                else -> AutoplayState.Waiting(room.notReady)
            }
            return
        }
        if (countdown?.isActive == true) return
        startCountdown()
    }

    private fun startCountdown() {
        countdown = vm.viewModelScope.launch {
            for (remaining in AUTOPLAY_COUNTDOWN_SECONDS downTo 1) {
                state.value = AutoplayState.CountingDown(remaining)
                delay(1.seconds)
                // Anything that changed the picture mid-count stops it: someone unreadied,
                // someone left, the room started playing anyway.
                if (!stillEligible()) {
                    state.value = AutoplayState.Idle
                    return@launch
                }
            }
            state.value = AutoplayState.Idle
            loggy("Autoplay: everyone is ready, starting the room")
            viewmodel.dispatcher.controlPlayback(Playback.PLAY, tellServer = true)
        }
    }

    private fun stillEligible(): Boolean {
        val session = viewmodel.session
        val room = summariseReadiness(session.userList.value, session.currentUsername)
        summary.value = room
        return shouldCountDown(
            autoplayEnabled = Preferences.AUTOPLAY.value(),
            roomPaused = viewmodel.protocol.globalPaused,
            canControlRoom = !session.isInControlledRoomWithoutController(),
            selfReady = session.ready.value,
            summary = room,
        )
    }

    private fun cancelCountdown() {
        countdown?.cancel()
        countdown = null
    }

    override fun invalidate() {
        cancelCountdown()
        state.value = AutoplayState.Idle
    }
}
