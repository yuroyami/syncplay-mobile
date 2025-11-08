package com.yuroyami.syncplay.managers.player

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.TrackChoices
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PlayerManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    lateinit var player: BasePlayer

    val isPlayerReady = MutableStateFlow(false)

    val media = MutableStateFlow<MediaFile?>(null)
    val hasVideo = media.map { it != null }
        .stateIn(
            scope = viewmodel.viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
    )

    var isNowPlaying = mutableStateOf(false)

    val timeFullMillis = MutableStateFlow<Long>(0L)

    val timeCurrentMillis = MutableStateFlow<Long>(0L)

    var currentTrackChoices: TrackChoices = TrackChoices()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun invalidate() {
        media.value = null
        isNowPlaying.value = false
        timeFullMillis.value = 0L
        timeCurrentMillis.value = 0L
        currentTrackChoices = TrackChoices()
    }

}