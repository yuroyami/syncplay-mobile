package com.yuroyami.syncplay.viewmodel

import com.yuroyami.syncplay.screens.home.JoinConfig

interface PlatformCallback {

    fun onLanguageChanged(newLang: String)

    /** Called when a user joins a room.
     *
     * @param [joinInfo] When null, the user is entering offline solo mode, otherwise joins an actual online room.
     */
    fun onJoin(joinInfo: JoinConfig?)

    fun onSaveConfigShortcut(joinInfo: JoinConfig)
    fun onEraseConfigShortcuts()

    fun getMaxVolume(): Int
    fun getCurrentVolume(): Int
    fun changeCurrentVolume(v: Int)

    fun getCurrentBrightness(): Float
    fun getMaxBrightness(): Float
    fun changeCurrentBrightness(v: Float)

    fun onLeave()

    fun onPlayback(paused: Boolean)

    fun onPictureInPicture(enable: Boolean)
}