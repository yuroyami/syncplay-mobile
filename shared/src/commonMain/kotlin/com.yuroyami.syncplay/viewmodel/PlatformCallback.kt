package com.yuroyami.syncplay.viewmodel

import com.yuroyami.syncplay.screens.home.JoinConfig

interface PlatformCallback {
    fun onLanguageChanged(newLang: String)

    fun onSaveConfigShortcut(joinInfo: JoinConfig)
    fun onEraseConfigShortcuts()

    fun getCurrentBrightness(): Float
    fun getMaxBrightness(): Float
    fun changeCurrentBrightness(v: Float)

    fun onLeave()

    fun onPlayback(paused: Boolean)

    fun onPictureInPicture(enable: Boolean)
}