package com.cosmik.syncplay.room

class Message {
    var sender: String? = null
    var timestamp: String = ""
    var timestampStylized: String = ""
    var content: String = ""
    var stylizedContent = ""

    /* Returns a ready-stylized string including/excluding a timestamp based on the boolean property */
    fun factorize(timestamp: Boolean): String {
        return if (timestamp) {
            "$timestampStylized$stylizedContent"
        } else {
            stylizedContent
        }
    }

}