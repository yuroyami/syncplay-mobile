package app.player

import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.player.models.MediaFile
import app.player.models.TrackChoices
import app.room.RoomViewmodel
import app.utils.generateTimestampMillis
import app.utils.platformCallback
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

class PlayerManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    lateinit var player: PlayerImpl
    val isPlayerReady = MutableStateFlow(false)
    val media = MutableStateFlow<MediaFile?>(null)

    val hasVideo = media.map { it != null }
        .stateIn(
            scope = viewmodel.viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    val isNowPlaying = MutableStateFlow(false)

    //TODO Remove in favor of media.fileDuration
    val timeFullMillis = MutableStateFlow<Long>(0L)

    //TODO Remove in favor of media.fileTimePos
    val timeCurrentMillis = MutableStateFlow<Long>(0L)

    /** Wall clock (ms) of the last [timeCurrentMillis] sample, for [estimatedPositionMs]. */
    @Volatile
    private var timeCurrentSampledAtMs: Long = 0L

    /** Records a fresh engine position. Every writer of [timeCurrentMillis] goes through here. */
    fun samplePosition(positionMs: Long) {
        timeCurrentSampledAtMs = generateTimestampMillis()
        timeCurrentMillis.value = positionMs
    }

    /**
     * The playhead now, from the last sample plus the time since it while the engine reports itself
     * playing. Safe from any thread; this is what the protocol reads instead of probing the engine
     * on the main thread. Mirrors the desktop client's `getPlayerPosition` extrapolation.
     */
    fun estimatedPositionMs(): Long {
        val sampled = timeCurrentMillis.value
        if (!isNowPlaying.value) return sampled
        val sampledAt = timeCurrentSampledAtMs
        if (sampledAt <= 0L) return sampled
        val age = generateTimestampMillis() - sampledAt
        // A stale sample is not extrapolated forever: past two seconds the tracker has stopped.
        return if (age in 0L..2_000L) sampled + age else sampled
    }

    /** Buffered position in ms, or -1 when the engine cannot say. */
    val timeBufferedMillis = MutableStateFlow(-1L)

    /**
     * Preserved across media changes so user's preferred tracks (e.g. Japanese audio)
     * carry over to the next playlist item without reverting to defaults.
     */
    var currentTrackChoices: TrackChoices = TrackChoices()

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    override fun invalidate() {
        // GlobalScope required — viewModelScope is already cancelled at this point.
        // destroy() must be exception-contained: GlobalScope has no parent to swallow a
        // failure, so an engine throwing mid-teardown would be an uncaught crash on the
        // way OUT of a room.
        platformCallback.mediaSessionFinalize()
        val closingPlayer = if (::player.isInitialized) player else null
        if (closingPlayer != null) {
            pendingDestroy = GlobalScope.launch {
                runCatching { closingPlayer.destroyAndReleaseMedia() }
                    .onFailure { app.utils.loggy("Player destroy failed: ${it.stackTraceToString()}") }
            }
        }
        media.value = null
        isNowPlaying.value = false
        timeFullMillis.value = 0L
        timeCurrentMillis.value = 0L
        timeBufferedMillis.value = -1L
        currentTrackChoices = TrackChoices()
    }

    companion object {
        /**
         * The teardown of the last room's engine, still running after its ViewModel is gone. The
         * next room joins it before building its own engine: mpv's handle is process-global, so a
         * fast leave-and-rejoin used to create the new core while the old one was still being
         * destroyed.
         */
        @Volatile
        private var pendingDestroy: Job? = null

        suspend fun awaitPendingDestroy() {
            pendingDestroy?.join()
            pendingDestroy = null
        }
    }
}
