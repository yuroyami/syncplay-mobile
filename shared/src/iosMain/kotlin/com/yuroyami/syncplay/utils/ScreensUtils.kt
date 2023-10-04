package com.yuroyami.syncplay.utils

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.yuroyami.syncplay.datastore.languageCallback
import com.yuroyami.syncplay.home.HomeConfig
import com.yuroyami.syncplay.home.HomeScreen
import com.yuroyami.syncplay.models.JoinInfo
import com.yuroyami.syncplay.player.avplayer.AvPlayer
import com.yuroyami.syncplay.watchroom.RoomUI
import com.yuroyami.syncplay.watchroom.player
import com.yuroyami.syncplay.watchroom.prepareProtocol


/**
 * A UIViewController is the iOS equivalent of an Activity from Android. So this will be
 * called from our iOS Swift code. This should contain all the components needed to be drawn
 * on the screen. */
fun WatchScreenControllerIOS() = ComposeUIViewController {
    RoomUI()
}

/**
 * A UIViewController is the iOS equivalent of an Activity from Android. So this will be
 * called from our iOS Swift code. This should contain all the components needed to be drawn
 * on the screen. */
fun HomeScreenControllerIOS(joinRoomLambda: () -> Unit) = ComposeUIViewController {
    languageCallback = remember {
        object : LanguageChange {
            override fun onLanguageChanged(newLang: String) {
                changeLanguage(newLang, null)
            }
        }
    }

    joinCallback = remember {
        object : JoinCallback {
            override fun onJoin(joinInfo: JoinInfo) {
                prepareProtocol(joinInfo.get())

                player = AvPlayer() //Initializing player upon joining room

                joinRoomLambda()
            }

            override fun onSaveConfigShortcut(joinInfo: JoinInfo) {
                //TODO
            }
        }
    }

    HomeScreen(remember { HomeConfig() })
}

