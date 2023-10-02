package com.yuroyami.syncplay.utils

import com.yuroyami.syncplay.models.JoinInfo

/*******************
 * GeneralUtils is a file that contains 'expect' declarations' to be used across iOS and android
 ******************/
expect interface LanguageChange {
    fun onLanguageChanged(newLang: String)
}

var joinCallback: JoinCallback? = null

expect interface JoinCallback {
    fun onJoin(joinInfo: JoinInfo)
}