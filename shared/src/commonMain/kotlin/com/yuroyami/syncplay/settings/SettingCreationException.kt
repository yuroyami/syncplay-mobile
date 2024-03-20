package com.yuroyami.syncplay.settings

class SettingCreationException(val string: String) : Exception() {

    override val message: String
        get() = run {
            val m = super.message
            if (m != null) "$m - $string" else string
        }
}