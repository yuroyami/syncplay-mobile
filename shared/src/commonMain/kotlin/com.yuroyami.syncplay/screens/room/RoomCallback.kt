package com.yuroyami.syncplay.screens.room

interface RoomCallback {

    fun onLeave()

    fun onPlayback(paused: Boolean)

    fun onPictureInPicture(enable: Boolean)
}