package com.yuroyami.syncplay.utils
import com.yuroyami.syncplay.models.JoinInfo

actual interface LanguageChange {
    actual fun onLanguageChanged(newLang: String)
}

actual interface JoinCallback {
    actual fun onJoin(joinInfo: JoinInfo)
}