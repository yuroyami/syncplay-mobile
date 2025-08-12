package com.yuroyami.syncplay.managers

import androidx.compose.runtime.mutableStateOf
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.TrackChoices
import com.yuroyami.syncplay.logic.managers.player.BasePlayer
import com.yuroyami.syncplay.logic.AbstractManager
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import kotlinx.coroutines.flow.MutableStateFlow

class PlayerManager(viewmodel: SyncplayViewmodel): AbstractManager(viewmodel) {

    var player: BasePlayer? = null
    var media  = mutableStateOf<MediaFile?>(null)

    val isNowPlaying = mutableStateOf(false)
    val timeFullMs = MutableStateFlow<Long>(0L)
    val timeCurrentMs = MutableStateFlow<Long>(0L)

    var currentTrackChoices: TrackChoices = TrackChoices()

}