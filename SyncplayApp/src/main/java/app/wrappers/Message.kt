package app.wrappers

import android.content.Context
import app.utils.MiscUtils.generateTimestamp
import app.utils.MiscUtils.getColorCode

/********************************************************************************************
 * Message wrapper class. It encapsulates all information and data we need about a message  *
 ********************************************************************************************/

class Message {
    var sender: String? = null
    var timestamp: String = generateTimestamp()
    var content: String = ""
    var isMainUser: Boolean = false

    /** Returns a ready-stylized string including/excluding a timestamp based on the boolean property */
    fun factorize(doTimestamp: Boolean, context: Context): String {
        val timestampHex = getColorCode("timestamp_color", context)
        val selftagHex = getColorCode("selftag_color", context)
        val friendtagHex = getColorCode("friendtag_color", context)
        val systemHex = getColorCode("systemtext_color", context)
        val userHex = getColorCode("usertext_color", context)

        val stylizedContent = if (sender != null) {
            if (isMainUser) {
                val username =
                    "<font color=\"${selftagHex}\"><strong><bold> $sender:</bold></strong></font>"
                "$username<font color=\"${userHex}\"><bold> $content</bold></font>"
            } else {
                val username =
                    "<font color=\"${friendtagHex}\"><strong><bold> $sender:</bold></strong></font>"
                "$username<font color=\"${userHex}\"><bold> $content</bold></font>"
            }
        } else {
            "<font color=\"${systemHex}\"><bold>$content</bold></font>"
        }

        return if (doTimestamp) {
            val timestampStylized = "<font color=\"${timestampHex}\">[${timestamp}] </font>"
            "$timestampStylized$stylizedContent"
        } else {
            val timestampStylized = "<font color=\"${timestampHex}\">-</font>"
            "$timestampStylized$stylizedContent"

        }
    }


}