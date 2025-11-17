package com.yuroyami.syncplay

import com.yuroyami.syncplay.models.JoinConfig
import com.yuroyami.syncplay.utils.platformCallback
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import platform.AVKit.AVPictureInPictureController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIApplicationLaunchOptionsShortcutItemKey
import platform.UIKit.UIApplicationShortcutItem
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.time.Duration.Companion.seconds

/**
 * Singleton instance of the UIApplicationDelegate for the Syncplay iOS app.
 *
 * Automatically registered with UIApplication on initialization. Provides a
 * reference for accessing the delegate's orientation mask from other parts of the code.
 */
val delegato = AppleDelegate().also {
    UIApplication.sharedApplication.delegate = it
}

/**
 * Global reference to the AVPictureInPictureController for video PiP functionality.
 *
 * Initialized when entering a room with video content. Allows Picture-in-Picture
 * control from anywhere in the app.
 */
var pipcontroller: AVPictureInPictureController? = null

/**
 * Application delegate for the Syncplay iOS app.
 *
 * This delegate is essential for managing iOS-specific behaviors that can't be
 * handled directly in the Compose UI layer.
 */
@Suppress("CONFLICTING_OVERLOADS")
class AppleDelegate : NSObject(), UIApplicationDelegateProtocol {

    /**
     * Current allowed orientation mask for the application.
     *
     * - Portrait when outside rooms (default navigation)
     * - Landscape when inside rooms (video playback)
     */
    var myOrientationMask: UIInterfaceOrientationMask = UIInterfaceOrientationMaskPortrait

    /**
     * Initializes the platform callback on delegate creation.
     */
    init {
        platformCallback = ApplePlatformCallback
    }

    /**
     * Provides the allowed interface orientations for a window.
     *
     * Called by iOS to determine which orientations are supported. Returns the
     * current value of [myOrientationMask] which changes based on room state.
     *
     * @param application The singleton app instance
     * @param supportedInterfaceOrientationsForWindow The window querying for orientations
     * @return Bitmask of allowed orientations
     */
    override fun application(
        application: UIApplication,
        supportedInterfaceOrientationsForWindow: UIWindow?
    ): UIInterfaceOrientationMask {
        return myOrientationMask
    }

    /**
     * Called when the application finishes launching.
     *
     * Checks if the app was launched via a Quick Action shortcut and handles it
     * by attempting to join the configured room.
     *
     * @param application The singleton app instance
     * @param didFinishLaunchingWithOptions Launch options dictionary
     * @return false to indicate default launch handling should continue
     */
    override fun application(application: UIApplication, didFinishLaunchingWithOptions: Map<Any?, *>?): Boolean {
        (didFinishLaunchingWithOptions?.get(UIApplicationLaunchOptionsShortcutItemKey) as? UIApplicationShortcutItem)
            ?.let { handleShortcut(it) }
        return false
    }

    /**
     * Called when a Quick Action shortcut is performed while the app is running.
     *
     * Handles the shortcut by joining the room encoded in the shortcut's type.
     * Calls the completion handler with true to indicate successful handling.
     *
     * @param application The singleton app instance
     * @param performActionForShortcutItem The shortcut item that was selected
     * @param completionHandler Callback to indicate if the shortcut was handled
     */
    override fun application(application: UIApplication, performActionForShortcutItem: UIApplicationShortcutItem, completionHandler: (Boolean) -> Unit) {
        handleShortcut(performActionForShortcutItem)
        completionHandler(true)

    }

}

/**
 * Temporary storage for the most recent shortcut item.
 *
 * Used to pass shortcut data to the UI layer after the delegate has processed it.
 * TODO: Replace with a more robust state management solution.
 */
var sc: UIApplicationShortcutItem? = null

/**
 * Processes a Quick Action shortcut item by extracting room configuration and joining.
 *
 * Parses the shortcut's type string (which encodes user, room, server info) and
 * triggers the room join flow.
 *
 * @param shortcut The UIApplicationShortcutItem containing room configuration
 */
fun handleShortcut(shortcut: UIApplicationShortcutItem) {
    println("HANDLE AMIGO SHORTCUT $shortcut")
    sc = shortcut

    //Our HomeViewmodel may not be ready on-the-go when opening the app
    MainScope().launch {
        runCatching {
            withTimeout(10.seconds) {
                while (homeViewmodel == null) {
                    delay(500)
                }
            }
            val joinConfig = Json.decodeFromString<JoinConfig>(shortcut.type)
            homeViewmodel?.joinRoom(joinConfig)
        }
    }
}