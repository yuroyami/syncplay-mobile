package com.yuroyami.syncplay.utils

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.ComposeUIViewControllerDelegate
import androidx.compose.ui.window.ComposeUIViewController
import com.yuroyami.syncplay.home.HomeCallback
import com.yuroyami.syncplay.home.HomeConfig
import com.yuroyami.syncplay.home.HomeScreen
import com.yuroyami.syncplay.home.snacky
import com.yuroyami.syncplay.models.JoinInfo
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.avplayer.AvPlayer
import com.yuroyami.syncplay.player.vlc.VlcPlayer
import com.yuroyami.syncplay.protocol.SpProtocolKtor
import com.yuroyami.syncplay.protocol.SyncplayProtocol
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.Setting
import com.yuroyami.syncplay.settings.SettingObtainerCallback
import com.yuroyami.syncplay.settings.obtainerCallback
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.watchroom.RoomCallback
import com.yuroyami.syncplay.watchroom.RoomUI
import com.yuroyami.syncplay.watchroom.homeCallback
import com.yuroyami.syncplay.watchroom.prepareProtocol
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.launch
import platform.AVKit.AVPictureInPictureController
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIApplicationLaunchOptionsShortcutItemKey
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIApplicationShortcutIcon.Companion.iconWithType
import platform.UIKit.UIApplicationShortcutIconType
import platform.UIKit.UIApplicationShortcutItem
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskAll
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.shortcutItems
import platform.darwin.NSObject

val delegato = AppleDelegate().also {
    UIApplication.sharedApplication.delegate = it
}


var pipcontroller: AVPictureInPictureController? = null

class AppleDelegate : NSObject(), UIApplicationDelegateProtocol {

    var myOrientationMask: UIInterfaceOrientationMask = UIInterfaceOrientationMaskPortrait


    init {
        homeCallback = Home
    }

    override fun application(
        application: UIApplication,
        supportedInterfaceOrientationsForWindow: UIWindow?
    ): UIInterfaceOrientationMask {
        return myOrientationMask
    }

    @Suppress("CONFLICTING_OVERLOADS")
    override fun application(application: UIApplication, didFinishLaunchingWithOptions: Map<Any?, *>?): Boolean {
        (didFinishLaunchingWithOptions?.get(UIApplicationLaunchOptionsShortcutItemKey) as? UIApplicationShortcutItem)
            ?.let { handleShortcut(it) }
        return false
    }

    @Suppress("CONFLICTING_OVERLOADS")
    override fun application(application: UIApplication, willFinishLaunchingWithOptions: Map<Any?, *>?): Boolean {
        (willFinishLaunchingWithOptions?.get(UIApplicationLaunchOptionsShortcutItemKey) as? UIApplicationShortcutItem)
            ?.let { handleShortcut(it) }
        return false
    }

    override fun application(application: UIApplication, performActionForShortcutItem: UIApplicationShortcutItem, completionHandler: (Boolean) -> Unit) {
        handleShortcut(performActionForShortcutItem)
        completionHandler(true)

    }
}

val isRoom = mutableStateOf(false)

var sc: UIApplicationShortcutItem? = null

/** This view controller hosts and switches between room screen and home screen view controllers */
fun SyncplayController() = ComposeUIViewController(configure = {
    delegate = AppleLifecycleWatchdog
}) {
    val room by remember { isRoom }

    when (room) {
        true -> {
            viewmodel?.roomCallback = Room

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
            LaunchedEffect(null) {
                delegato.myOrientationMask = UIInterfaceOrientationMaskAll

                UIApplication.sharedApplication.connectedScenes.firstOrNull()?.let {
                    (it as? UIWindowScene)?.requestGeometryUpdateWithPreferences(
                        UIWindowSceneGeometryPreferencesIOS(
                            UIInterfaceOrientationMaskAll
                        ),
                        null
                    )
                }
            }

            HomeScreen(remember { HomeConfig() })
        }
    }
}

object Home : HomeCallback {

    override fun onLanguageChanged(newLang: String) {
        NSURL(string = UIApplicationOpenSettingsURLString).let { url ->
            UIApplication.sharedApplication.openURL(url, mapOf<Any?, Any>(), null)
        }
    }

    override fun onJoin(joinInfo: JoinInfo?) {
        viewmodel = com.yuroyami.syncplay.watchroom.SpViewModel()

        joinInfo?.let {
            val networkEngine = SyncplayProtocol.getPreferredEngine()
            viewmodel!!.p = if (networkEngine == SyncplayProtocol.NetworkEngine.KTOR) {
                SpProtocolKtor()
            } else {
                instantiateSyncplayProtocolSwiftNIO!!.invoke()
            }

            prepareProtocol(it)
        }

        val videoEngine = BasePlayer.ENGINE.valueOf(
            valueBlockingly(DataStoreKeys.MISC_PLAYER_ENGINE, getDefaultEngine())
        )

        when (videoEngine) {
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
        val type = with(joinInfo) { listOf(username, roomname, address, port.toString(), password) }
            .joinToString("','#'")

        val shortcutItem = UIApplicationShortcutItem(
            type = type,
            localizedTitle = joinInfo.roomname,
            localizedSubtitle = null,
            icon = iconWithType(UIApplicationShortcutIconType.UIApplicationShortcutIconTypeFavorite),
            userInfo = null
        )

        // Add the shortcut item to the application
        UIApplication.sharedApplication.shortcutItems = UIApplication.sharedApplication.shortcutItems?.plus(shortcutItem)

        viewmodel?.viewmodelScope?.launch {
            snacky.showSnackbar(
                "Shortcut added: ${joinInfo.roomname}"
            )
        }
    }

    override fun onEraseConfigShortcuts() {
        UIApplication.sharedApplication.shortcutItems = emptyList<UIApplicationShortcutItem>()
    }
}

fun handleShortcut(shortcut: UIApplicationShortcutItem) {
    println("HANDLE AMIGO SHORTCUT $shortcut")
    sc = shortcut
    homeCallback?.onJoin(shortcut.type.toJoinInfo().get())
}

fun String.toJoinInfo(): JoinInfo {
    val l = split("','#'")
    return JoinInfo(
        username = l[0], roomname = l[1],
        address = l[2], port = l[3].toIntOrNull() ?: 0, password = l[4]
    )
}

object Room : RoomCallback {
    override fun onLeave() {
        isRoom.value = false
        viewmodel = null

    }

    override fun onPlayback(paused: Boolean) {

    }

    override fun onPictureInPicture(enable: Boolean) {
        if (AVPictureInPictureController.isPictureInPictureSupported()) {
            val layer = when (viewmodel?.player) {
                is AvPlayer -> {
                    (viewmodel?.player as? AvPlayer)?.avPlayerLayer
                }

                is VlcPlayer -> {
                    (viewmodel?.player as? VlcPlayer)?.pipLayer
                }

                else -> null
            }
            layer?.let {
                pipcontroller = AVPictureInPictureController(layer)
            }
        }

        if (pipcontroller?.pictureInPicturePossible == true
            && isRoom.value
            && viewmodel?.media != null
        ) {
            pipcontroller?.startPictureInPicture()
        }
    }
}


object AppleLifecycleWatchdog: ComposeUIViewControllerDelegate {
    private val watchdog by lazy { viewmodel?.lifecycleWatchdog }

    override fun viewDidAppear(animated: Boolean) {
        watchdog?.onResume()
    }

    override fun viewDidDisappear(animated: Boolean) {
        watchdog?.onStop()
    }

    override fun viewDidLoad() {
        watchdog?.onCreate()
    }

    override fun viewWillAppear(animated: Boolean) {
        watchdog?.onStart()
    }

    override fun viewWillDisappear(animated: Boolean) {
        watchdog?.onPause()
    }
}