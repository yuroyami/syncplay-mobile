package com.cosmik.syncplay.room

object RoomUtils {

    @JvmStatic
    fun timeStamper(seconds: Int): String {
        return if (seconds < 3600) {
            String.format("%02d:%02d", (seconds / 60) % 60, seconds % 60)
        } else {
            String.format("%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
        }
    }

}