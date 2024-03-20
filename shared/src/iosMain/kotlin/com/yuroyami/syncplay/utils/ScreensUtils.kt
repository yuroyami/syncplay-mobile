package com.yuroyami.syncplay.utils

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.yuroyami.syncplay.home.HomeCallback
import com.yuroyami.syncplay.home.HomeConfig
import com.yuroyami.syncplay.home.HomeScreen
import com.yuroyami.syncplay.models.JoinInfo
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.avplayer.AvPlayer
import com.yuroyami.syncplay.player.vlc.VlcPlayer
import com.yuroyami.syncplay.protocol.SpProtocolApple
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.Setting
import com.yuroyami.syncplay.settings.SettingObtainerCallback
import com.yuroyami.syncplay.settings.obtainerCallback
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.watchroom.RoomUI
import com.yuroyami.syncplay.watchroom.homeCallback
import com.yuroyami.syncplay.watchroom.prepareProtocol
import com.yuroyami.syncplay.watchroom.viewmodel
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskAll
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.darwin.NSObject

val delegato = AppleDelegate()
class AppleDelegate : NSObject(), UIApplicationDelegateProtocol {
    var myOrientationMask = UIInterfaceOrientationMaskPortrait

    override fun application(
        application: UIApplication,
        supportedInterfaceOrientationsForWindow: UIWindow?
    ): UIInterfaceOrientationMask {
        return myOrientationMask
    }
}

val isRoom = mutableStateOf(false)

/** This view controller hosts and switches between room screen and home screen view controllers */
fun SyncplayController() = ComposeUIViewController {
    val room by remember { isRoom }

    when (room) {
        true -> {
            LaunchedEffect(null) {
                delegato.myOrientationMask = UIInterfaceOrientationMaskLandscape

                UIApplication.sharedApplication.connectedScenes.firstOrNull()?.let {
                    (it as? UIWindowScene)?.apply {
                        requestGeometryUpdateWithPreferences(
                            UIWindowSceneGeometryPreferencesIOS(
                                UIInterfaceOrientationMaskLandscape
                            ),
                            null
                        )
                    }
                }
            }

            RoomUI()
        }

        false -> {
            homeCallback = Home

            LaunchedEffect(null) {
                with(UIApplication.sharedApplication) {
                    delegate = delegato
                }

                delegato.myOrientationMask = UIInterfaceOrientationMaskAll


                UIApplication.sharedApplication.connectedScenes.firstOrNull()?.let {
                    (it as? UIWindowScene)?.requestGeometryUpdateWithPreferences(
                        UIWindowSceneGeometryPreferencesIOS(
                            UIInterfaceOrientationMaskAll
                        ),
                        null
                    )
                }
//                UIDevice.currentDevice().setValue(
//                    UIInterfaceOrientationPortrait,
//                    "orientation"
//                )
            }

            HomeScreen(remember { HomeConfig() })
        }
    }
}

object Home : HomeCallback {
    override fun onLanguageChanged(newLang: String) {

    }

    override fun onJoin(joinInfo: JoinInfo) {
        viewmodel = com.yuroyami.syncplay.watchroom.SpViewModel()
        viewmodel!!.p = SpProtocolApple()

        prepareProtocol(joinInfo.get())

        val engine = BasePlayer.ENGINE.valueOf(
            valueBlockingly(DataStoreKeys.MISC_PLAYER_ENGINE, getDefaultEngine())
        )

        when (engine) {
            BasePlayer.ENGINE.IOS_AVPLAYER -> {
                viewmodel?.player = AvPlayer()
            }

            BasePlayer.ENGINE.IOS_VLC -> {
                viewmodel?.player = VlcPlayer()
            }

            else -> {}
        }

        obtainerCallback = object : SettingObtainerCallback {
            override fun getMoreRoomSettings() = emptyList<Pair<Setting<out Comparable<*>>, String>>()
        }

        isRoom.value = true
    }

    override fun onSaveConfigShortcut(joinInfo: JoinInfo) {

    }

    override fun onSoloMode() {

    }
}

