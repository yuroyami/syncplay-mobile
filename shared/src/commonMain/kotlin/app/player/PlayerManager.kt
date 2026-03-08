package app.player

import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.player.models.MediaFile
import app.player.models.TrackChoices
import app.room.RoomViewmodel
import app.utils.platformCallback
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    /**
     * Preserved across media changes so user's preferred tracks (e.g. Japanese audio)
     * carry over to the next playlist item without reverting to defaults.
     */
    var currentTrackChoices: TrackChoices = TrackChoices()

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    override fun invalidate() {
        // GlobalScope required — viewModelScope is already cancelled at this point
        platformCallback.mediaSessionFinalize()
        GlobalScope.launch {
            player.destroy()
        }
        media.value = null
        isNowPlaying.value = false
        timeFullMillis.value = 0L
        timeCurrentMillis.value = 0L
        currentTrackChoices = TrackChoices()
    }
}