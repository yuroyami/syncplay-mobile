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
import app.protocol.models.ConnectionState
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Tracks who the room is waiting for, and runs the autoplay countdown once it waits for nobody.
 * A room is the group of people watching together. Like the desktop Syncplay client, the room
 * starts playback on its own when every user with a file is ready.
 *
 * The decisions ([summariseReadiness], [shouldCountDown]) live in `Readiness.kt` and are tested
 * there. This class only watches, times and acts.
 */
class ReadinessManager(private val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    /** What the room should show about readiness right now. */
    val state: StateFlow<AutoplayState>
        field = MutableStateFlow<AutoplayState>(AutoplayState.Idle)

    /** Who the room is waiting for, if anyone. */
    val summary: StateFlow<ReadinessSummary>
        field = MutableStateFlow(ReadinessSummary(emptyList(), emptyList()))

    private var countdown: Job? = null
    private var roster: Job? = null

    /**
     * Starts watching the roster (the list of users in the room) and calls [evaluate] on every
     * roster change.
     *
     * Runs on every connect, so a second call while the collector runs does nothing. Without
     * this guard, each reconnect would add a collector, and one roster change would run
     * [evaluate] once per collector.
     */
    fun start() {
        if (viewmodel.isSoloMode) return
        if (roster?.isActive == true) return
        roster = onIOThread {
            viewmodel.session.userList.collect { evaluate() }
        }
    }

    /** Stops watching the roster and cancels the countdown. Called when the connection drops. */
    fun stop() {
        roster?.cancel()
        roster = null
        cancelCountdown()
        state.value = AutoplayState.Idle
    }

    /**
     * Reads the room's readiness again, then starts or cancels the countdown. Called on every
     * roster change and after anything that can change readiness: the local user's ready
     * toggle, a pause, a play.
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
                // A change during the count stops the countdown: a user is no longer ready, a
                // user left, or the room started playing.
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
        // Without a connection, the play at the end of the countdown would reach no one.
        if (viewmodel.networkManager.state.value != ConnectionState.CONNECTED) return false
        if (viewmodel.uiState.isInBackground || viewmodel.media == null) return false
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

    override fun invalidate() = stop()
}
