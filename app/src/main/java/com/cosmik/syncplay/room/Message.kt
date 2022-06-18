package com.cosmik.syncplay.room

class Message {
    var sender: String? = null

    var timestamp: String = ""
    var timestampStylized: String = ""

    var content: String = ""
    var stylizedContent = ""

    /* This essentially means getting a full & ready string output based on the boolean. */
    fun factorize(timestamp: Boolean): String {
        return if (timestamp) {
            "$timestampStylized$stylizedContent"
        } else {
            stylizedContent
        }
    }

}