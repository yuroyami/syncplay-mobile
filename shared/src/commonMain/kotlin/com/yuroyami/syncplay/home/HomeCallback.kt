package com.yuroyami.syncplay.home

import com.yuroyami.syncplay.models.JoinInfo

interface HomeCallback {

    fun onLanguageChanged(newLang: String)

    /** Called when a user joins a room.
     *
     * @param [joinInfo] When null, the user is entering offline solo mode, otherwise joins an actual online room.
     */
    fun onJoin(joinInfo: JoinInfo?)

    fun onSaveConfigShortcut(joinInfo: JoinInfo)
    fun onEraseConfigShortcuts()
}