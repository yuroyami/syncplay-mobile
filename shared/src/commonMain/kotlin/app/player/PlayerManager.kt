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
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull
import app.utils.loggy
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

    /**
     * True while the engine fills its buffer or opens media, instead of showing frames.
     *
     * Every engine reports this in its own way (a state constant, a cache property, a status
     * enum). Each engine copies it here, so the room shows one indicator for every engine. It is
     * for display only: nothing in the sync path reads it, because a stalled buffer is not a
     * pause and must never be broadcast as one.
     */
    val isBuffering = MutableStateFlow(false)

    //TODO Remove in favor of media.fileDuration
    val timeFullMillis = MutableStateFlow<Long>(0L)

    //TODO Remove in favor of media.fileTimePos
    val timeCurrentMillis = MutableStateFlow<Long>(0L)

    /** Wall clock (ms) of the last [timeCurrentMillis] sample, for [estimatedPositionMs]. */
    @Volatile
    private var timeCurrentSampledAtMs: Long = 0L

    /** Records a new engine position. Every position update goes through here. */
    fun samplePosition(positionMs: Long) {
        timeCurrentSampledAtMs = generateTimestampMillis()
        timeCurrentMillis.value = positionMs
    }

    /**
     * The playhead now: the last sample, plus the time since that sample while the engine reports
     * that it plays. Safe to call from any thread. The protocol reads this, so it never has to ask
     * the engine on the main thread. It mirrors `getPlayerPosition` of the Syncplay PC client.
     */
    fun estimatedPositionMs(): Long {
        val sampled = timeCurrentMillis.value
        if (!isNowPlaying.value) return sampled
        val sampledAt = timeCurrentSampledAtMs
        if (sampledAt <= 0L) return sampled
        val age = generateTimestampMillis() - sampledAt
        // Do not extrapolate a stale sample forever. After two seconds, the tracker has stopped.
        return if (age in 0L..2_000L) sampled + age else sampled
    }

    /** Buffered position in ms, or -1 when the engine cannot say. */
    val timeBufferedMillis = MutableStateFlow(-1L)

    /**
     * The user's track choices. They stay across media changes, so a chosen track (for example
     * Japanese audio) carries over to the next playlist item.
     */
    var currentTrackChoices: TrackChoices = TrackChoices()

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    override fun invalidate() {
        // GlobalScope, because viewModelScope is already cancelled at this point.
        // The destroy call must catch its own failures. GlobalScope has no parent to absorb an
        // exception, so an engine that throws during teardown would crash the app while the
        // user leaves the room.
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
        isBuffering.value = false
    }

    companion object {
        /**
         * The teardown of the last room's engine, still running after its ViewModel is gone. The
         * next room waits for it before building its own engine. mpv's handle is process-global,
         * so a fast leave and rejoin would otherwise create the new core while the old one is
         * still being destroyed.
         */
        @Volatile
        private var pendingDestroy: Job? = null

        /**
         * Waits for the previous room's engine to finish its teardown, for at most [TEARDOWN_WAIT].
         *
         * The next room builds its engine after this and only then opens its connection. Without
         * the limit, a teardown that hangs would mean a room that never connects, with nothing on
         * screen to say why. The wait exists because mpv's handle is process-global. After the
         * limit, going ahead is a better failure than never joining.
         */
        suspend fun awaitPendingDestroy() {
            val pending = pendingDestroy ?: return
            val waited = TimeSource.Monotonic.markNow()
            val finished = withTimeoutOrNull(TEARDOWN_WAIT) { pending.join() } != null
            if (finished) {
                loggy("Previous player torn down in ${waited.elapsedNow().inWholeMilliseconds}ms")
            } else {
                loggy("Previous player still tearing down after ${TEARDOWN_WAIT.inWholeSeconds}s; starting the room anyway")
            }
            pendingDestroy = null
        }

        /** How long a new room waits for the last room's engine before it goes ahead. */
        private val TEARDOWN_WAIT = 6.seconds
    }
}
