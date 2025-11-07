package com.yuroyami.syncplay

import com.yuroyami.syncplay.models.JoinConfig
import com.yuroyami.syncplay.viewmodels.HomeViewmodel
import platform.AVKit.AVPictureInPictureController
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIApplicationShortcutIcon.Companion.iconWithType
import platform.UIKit.UIApplicationShortcutIconType
import platform.UIKit.UIApplicationShortcutItem
import platform.UIKit.UIInterfaceOrientationMaskAll
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIScreen
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.shortcutItems

object ApplePlatformCallback : PlatformCallback {
    override fun onPlayback(paused: Boolean) {
        //TODO: Here we only need to update PIP pause/play buttons or other params.
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

    override fun onRoomEnterOrLeave(event: PlatformCallback.RoomEvent) {
        //We change app orientation based on whether we're inside the room or not
        val correspondingOrientation = when (event) {
            PlatformCallback.RoomEvent.ENTER -> UIInterfaceOrientationMaskLandscape
            PlatformCallback.RoomEvent.LEAVE -> UIInterfaceOrientationMaskAll
        }

        delegato.myOrientationMask = correspondingOrientation

        UIApplication.sharedApplication.connectedScenes.firstOrNull()?.let {
            (it as? UIWindowScene)?.apply {
                requestGeometryUpdateWithPreferences(
                    geometryPreferences = UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = correspondingOrientation),
                    errorHandler = null
                )
            }
        }
    }

    override fun onLanguageChanged(newLang: String) {
        NSURL(string = UIApplicationOpenSettingsURLString).let { url ->
            UIApplication.sharedApplication.openURL(url, mapOf<Any?, Any>(), null)
        }
    }

    override fun HomeViewmodel.onSaveConfigShortcut(joinInfo: JoinConfig) {
        val type = with(joinInfo) { listOf(user, room, ip, port.toString(), pw) }.joinToString("','#'")

        val shortcutItem = UIApplicationShortcutItem(
            type = type,
            localizedTitle = joinInfo.room,
            localizedSubtitle = null,
            icon = iconWithType(UIApplicationShortcutIconType.UIApplicationShortcutIconTypeFavorite),
            userInfo = null
        )

        // Add the shortcut item to the application
        UIApplication.sharedApplication.shortcutItems = UIApplication.sharedApplication.shortcutItems?.plus(shortcutItem)

        //TODO: Localize
        snackManager.snackItAsync("Shortcut added: ${joinInfo.room}")
    }

    override fun onEraseConfigShortcuts() {
        UIApplication.sharedApplication.shortcutItems = emptyList<UIApplicationShortcutItem>()
    }
}