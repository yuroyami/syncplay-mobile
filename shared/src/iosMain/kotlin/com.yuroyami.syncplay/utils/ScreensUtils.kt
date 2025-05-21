package com.yuroyami.syncplay.utils

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.uikit.ComposeUIViewControllerDelegate
import androidx.compose.ui.window.ComposeUIViewController
import com.yuroyami.syncplay.screens.adam.AdamScreen
import com.yuroyami.syncplay.screens.home.JoinConfig
import com.yuroyami.syncplay.viewmodel.PlatformCallback
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
import platform.UIKit.UIScreen
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.shortcutItems
import platform.darwin.NSObject

val delegato = AppleDelegate().also {
    UIApplication.sharedApplication.delegate = it
}

var pipcontroller: AVPictureInPictureController? = null

@Suppress("CONFLICTING_OVERLOADS")
class AppleDelegate : NSObject(), UIApplicationDelegateProtocol {
    var myOrientationMask: UIInterfaceOrientationMask = UIInterfaceOrientationMaskPortrait

    init {
        platformCallback = PlatformCallback
    }

    override fun application(
        application: UIApplication,
        supportedInterfaceOrientationsForWindow: UIWindow?
    ): UIInterfaceOrientationMask {
        return myOrientationMask
    }

    override fun application(application: UIApplication, didFinishLaunchingWithOptions: Map<Any?, *>?): Boolean {
        (didFinishLaunchingWithOptions?.get(UIApplicationLaunchOptionsShortcutItemKey) as? UIApplicationShortcutItem)
            ?.let { handleShortcut(it) }
        return false
    }

    override fun application(application: UIApplication, performActionForShortcutItem: UIApplicationShortcutItem, completionHandler: (Boolean) -> Unit) {
        handleShortcut(performActionForShortcutItem)
        completionHandler(true)

    }
}

var sc: UIApplicationShortcutItem? = null

/** This view controller hosts and switches between room screen and home screen view controllers */
fun SyncplayController() = ComposeUIViewController(configure = {
    delegate = AppleLifecycleWatchdog
}) {

    LaunchedEffect(null) {
        val isInRoom = false

        delegato.myOrientationMask = when (isInRoom) {
            true -> UIInterfaceOrientationMaskLandscape
            false -> UIInterfaceOrientationMaskAll
        }

        UIApplication.sharedApplication.connectedScenes.firstOrNull()?.let {
            (it as? UIWindowScene)?.apply {
                requestGeometryUpdateWithPreferences(
                    UIWindowSceneGeometryPreferencesIOS(
                        if (isInRoom) UIInterfaceOrientationMaskLandscape else UIInterfaceOrientationMaskAll
                    ),
                    null
                )
            }
        }
    }

    AdamScreen()
}

object PlatformCallback : PlatformCallback {

    override fun onLeave() {
    }

    override fun onPlayback(paused: Boolean) {

    }

    override fun onPictureInPicture(enable: Boolean) {
        if (AVPictureInPictureController.isPictureInPictureSupported()) {
            /* TODO val layer = when (viewmodel?.player) {
                is AvPlayer -> (viewmodel?.player as? AvPlayer)?.avPlayerLayer
                is VlcPlayer -> (viewmodel?.player as? VlcPlayer)?.pipLayer
                else -> null
            }
            layer?.let {
                pipcontroller = AVPictureInPictureController(layer)
            } */
        }

        if (pipcontroller?.pictureInPicturePossible == true
            //TODO && isRoom.value
            //TODO && viewmodel?.media != null
        ) {
            pipcontroller?.startPictureInPicture()
        }
    }

    private const val MAX_VOLUME = 100
    private const val MAX_BRIGHTNESS = 1.0f

    override fun getMaxBrightness() = MAX_BRIGHTNESS
    override fun getCurrentBrightness(): Float = UIScreen.mainScreen.brightness.toFloat()
    override fun changeCurrentBrightness(v: Float) {
        UIScreen.mainScreen.brightness = v.coerceIn(0.0f, MAX_BRIGHTNESS).toDouble()
    }

    override fun onLanguageChanged(newLang: String) {
        NSURL(string = UIApplicationOpenSettingsURLString).let { url ->
            UIApplication.sharedApplication.openURL(url, mapOf<Any?, Any>(), null)
        }
    }

    override fun onSaveConfigShortcut(joinInfo: JoinConfig) {
        val type = with(joinInfo) { listOf(user, room, ip, port.toString(), pw) }
            .joinToString("','#'")

        val shortcutItem = UIApplicationShortcutItem(
            type = type,
            localizedTitle = joinInfo.room,
            localizedSubtitle = null,
            icon = iconWithType(UIApplicationShortcutIconType.UIApplicationShortcutIconTypeFavorite),
            userInfo = null
        )

        // Add the shortcut item to the application
        UIApplication.sharedApplication.shortcutItems = UIApplication.sharedApplication.shortcutItems?.plus(shortcutItem)

        /* TODO viewmodel?.viewmodelScope?.launch {
            snacky.showSnackbar(
                "Shortcut added: ${joinInfo.roomname}"
            )
        } */
    }

    override fun onEraseConfigShortcuts() {
        UIApplication.sharedApplication.shortcutItems = emptyList<UIApplicationShortcutItem>()
    }
}

fun handleShortcut(shortcut: UIApplicationShortcutItem) {
    println("HANDLE AMIGO SHORTCUT $shortcut")
    sc = shortcut
    //TODO homeCallback?.onJoin(shortcut.type.toJoinInfo().get())
}

fun String.toJoinConfig(): JoinConfig {
    val l = split("','#'")
    return JoinConfig(
        user = l[0], room = l[1],
        ip = l[2], port = l[3].toIntOrNull() ?: 0, pw = l[4]
    )
}


object AppleLifecycleWatchdog : ComposeUIViewControllerDelegate {
    //TODO private val watchdog by lazy { viewmodel?.lifecycleWatchdog }

    /*
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
    }*/
}