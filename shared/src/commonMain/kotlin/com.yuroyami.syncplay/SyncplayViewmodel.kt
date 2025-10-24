package com.yuroyami.syncplay

import androidx.lifecycle.ViewModel
import com.yuroyami.syncplay.managers.ThemeManager

class SyncplayViewmodel: ViewModel() {

    /** Manages themes */
    val themeManager: ThemeManager by lazy { ThemeManager(this) }

    /** Tracks whether the user has entered a room before, for UI/UX stuff */
    var hasEnteredRoomOnce = false
}