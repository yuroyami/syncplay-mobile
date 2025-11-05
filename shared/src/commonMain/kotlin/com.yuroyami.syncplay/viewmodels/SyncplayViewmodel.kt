package com.yuroyami.syncplay.viewmodels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.yuroyami.syncplay.managers.ThemeManager

class SyncplayViewmodel : ViewModel() {

    //TODO advertise this to server, disable shared-playlist-related functionality everywhere when this is off
    val isSharedPlaylistEnabled = mutableStateOf(true)

    /** Manages themes */
    val themeManager: ThemeManager by lazy { ThemeManager(this) }

    /** Tracks whether the user has entered a room before, for UI/UX stuff */
    var hasEnteredRoomOnce = false
}