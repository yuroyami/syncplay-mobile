package com.yuroyami.syncplay.managers

import androidx.compose.runtime.mutableStateOf
import com.yuroyami.syncplay.logic.AbstractManager
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import com.yuroyami.syncplay.logic.player.BasePlayer
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.TrackChoices
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class PlayerManager(viewmodel: SyncplayViewmodel): AbstractManager(viewmodel) {

    var player: BasePlayer? = null

    val media = MutableStateFlow<MediaFile?>(null)
    val hasVideo: Flow<Boolean> = media.map { it != null }

    var isNowPlaying =  mutableStateOf(false)

    val timeFullMillis = MutableStateFlow<Long>(0L)

    val timeCurrentMillis = MutableStateFlow<Long>(0L)

    var currentTrackChoices: TrackChoices = TrackChoices()

}