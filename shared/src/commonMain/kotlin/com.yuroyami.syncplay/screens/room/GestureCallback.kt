package com.yuroyami.syncplay.screens.room

interface GestureCallback {
    fun getMaxVolume(): Int
    fun getCurrentVolume(): Int
    fun changeCurrentVolume(v: Int)

    fun getCurrentBrightness(): Float
    fun getMaxBrightness(): Float
    fun changeCurrentBrightness(v: Float)
}