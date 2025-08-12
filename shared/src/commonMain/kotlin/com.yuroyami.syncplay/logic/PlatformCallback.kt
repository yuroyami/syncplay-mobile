package com.yuroyami.syncplay.logic

import com.yuroyami.syncplay.models.JoinConfig

interface PlatformCallback {
    fun onLanguageChanged(newLang: String)

    fun onSaveConfigShortcut(joinInfo: JoinConfig)
    fun onEraseConfigShortcuts()

    fun getCurrentBrightness(): Float
    fun getMaxBrightness(): Float
    fun changeCurrentBrightness(v: Float)

    enum class RoomEvent { LEAVE, ENTER }
    fun onRoomEnterOrLeave(event: RoomEvent)

    fun onPlayback(paused: Boolean)

    fun onPictureInPicture(enable: Boolean)
}