package com.yuroyami.syncplay.managers

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.logic.AbstractManager
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import com.yuroyami.syncplay.logic.player.BasePlayer
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.TrackChoices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PlayerManager(viewmodel: SyncplayViewmodel): AbstractManager(viewmodel) {

    var player: BasePlayer? = null

    val media = MutableStateFlow<MediaFile?>(null)
    val hasVideo = media.map { it != null }
        .stateIn(
            scope = viewmodel.viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
    )

    var isNowPlaying =  mutableStateOf(false)

    val timeFullMillis = MutableStateFlow<Long>(0L)

    val timeCurrentMillis = MutableStateFlow<Long>(0L)

    var currentTrackChoices: TrackChoices = TrackChoices()

}