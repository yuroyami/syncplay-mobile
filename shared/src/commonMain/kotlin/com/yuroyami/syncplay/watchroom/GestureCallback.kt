package com.yuroyami.syncplay.watchroom

interface GestureCallback {
    fun getMaxVolume(): Int
    fun getCurrentVolume(): Int
    fun changeCurrentVolume(v: Int)

    fun getCurrentBrightness(): Float
    fun getMaxBrightness(): Float
    fun changeCurrentBrightness(v: Float)
}