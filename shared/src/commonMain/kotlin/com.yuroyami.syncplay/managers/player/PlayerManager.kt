package com.yuroyami.syncplay.managers.player

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.TrackChoices
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manages the media player instance and playback state for the Syncplay room.
 *
 * The player instance is lazily initialized during room setup based on user preferences
 * or platform defaults.
 *
 * @property viewmodel The parent RoomViewModel that owns this manager
 */
class PlayerManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    /**
     * The active media player instance.
     * Initialized during room setup with the user's preferred player engine (or the platform default one if unchanged)
     */
    lateinit var player: BasePlayer

    /**
     * Whether the player has been initialized and is ready for use.
     * Used to gate operations that require a fully configured player.
     */
    val isPlayerReady = MutableStateFlow(false)

    /**
     * The currently loaded media file, if any.
     * Null when no media is loaded or in an empty room state.
     */
    val media = MutableStateFlow<MediaFile?>(null)

    /**
     * Derived state indicating whether any media with video is currently loaded.
     * Used to show/hide video-specific UI elements.
     */
    val hasVideo = media.map { it != null }
        .stateIn(
            scope = viewmodel.viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    /**
     * Whether media is currently playing (not paused).
     * Used for UI state like play/pause button icons.
     */
    var isNowPlaying = mutableStateOf(false)

    /**
     * Total duration of the currently loaded media in milliseconds.
     * Zero when no media is loaded or duration is unknown.
     */
    //TODO Remove in favor of media.fileDuration
    val timeFullMillis = MutableStateFlow<Long>(0L)

    /**
     * Current playback position in milliseconds.
     * Updated continuously by the player's progress tracking job.
     */
    //TODO Remove in favor of media.fileTimePos
    val timeCurrentMillis = MutableStateFlow<Long>(0L)

    /**
     * User's current audio and subtitle track selections.
     * Preserved across media changes to maintain preferences within a playlist.
     *
     * For example: A user loads an episode with Japanese track that is not selected by default,
     * then he switches to the next episode, it keeps the Japanese track even though it's not the forced/default track.
     *
     * This is also used to maintain state across configuration changes (such as closing the app momentarily)
     */
    var currentTrackChoices: TrackChoices = TrackChoices()

    /**
     * Resets all playback state to initial values.
     * Called when leaving the room or clearing the player.
     * Does not destroy the player instance itself.
     */
    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    override fun invalidate() {
        GlobalScope.launch {
            //Has to run in GlobalScope because room viewmodelscope is cancelled at this point of invalidation
            player.destroy()
        }
        media.value = null
        isNowPlaying.value = false
        timeFullMillis.value = 0L
        timeCurrentMillis.value = 0L
        currentTrackChoices = TrackChoices()
    }

}