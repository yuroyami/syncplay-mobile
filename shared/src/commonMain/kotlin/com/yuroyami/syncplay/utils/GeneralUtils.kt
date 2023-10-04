package com.yuroyami.syncplay.utils

import com.yuroyami.syncplay.models.JoinInfo

/*******************
 * GeneralUtils is a file that contains shared interfaces to be used across iOS and android
 ******************/
interface LanguageChange {
    fun onLanguageChanged(newLang: String)
}

var joinCallback: JoinCallback? = null

interface JoinCallback {
    fun onJoin(joinInfo: JoinInfo)

    fun onSaveConfigShortcut(joinInfo: JoinInfo)
}