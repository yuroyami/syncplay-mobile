package com.yuroyami.syncplay.home

import com.yuroyami.syncplay.models.JoinInfo

interface HomeCallback {

    fun onLanguageChanged(newLang: String)

    fun onJoin(joinInfo: JoinInfo)

    fun onSaveConfigShortcut(joinInfo: JoinInfo)

    fun onSoloMode()

}