package app

import app.utils.platformCallback
import app.home.JoinConfig
import kotlinx.coroutines.flow.MutableStateFlow
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

/** App delegate singleton, registered with UIApplication on init. */
val delegato = AppleDelegate().also {
    UIApplication.sharedApplication.delegate = it
}

/** Global AVPictureInPictureController, set when entering a room with video. */
var pipcontroller: AVPictureInPictureController? = null

/** UIApplicationDelegate handling iOS behaviors the Compose layer cannot: orientation mask and Quick Action shortcuts. */
@Suppress("CONFLICTING_OVERLOADS")
class AppleDelegate : NSObject(), UIApplicationDelegateProtocol {

    /** Allowed orientations: portrait outside rooms, landscape inside rooms. iOS reads this via [application]. */
    var myOrientationMask: UIInterfaceOrientationMask = UIInterfaceOrientationMaskPortrait

    init {
        platformCallback = ApplePlatformCallback
    }

    override fun application(
        application: UIApplication,
        supportedInterfaceOrientationsForWindow: UIWindow?
    ): UIInterfaceOrientationMask {
        return myOrientationMask
    }

    /** Cold-launch entry point: routes a launching Quick Action shortcut to [handleShortcut]. */
    override fun application(application: UIApplication, didFinishLaunchingWithOptions: Map<Any?, *>?): Boolean {
        (didFinishLaunchingWithOptions?.get(UIApplicationLaunchOptionsShortcutItemKey) as? UIApplicationShortcutItem)
            ?.let { handleShortcut(it) }
        return false
    }

    /** Handles a Quick Action tapped while the app is already running. */
    override fun application(application: UIApplication, performActionForShortcutItem: UIApplicationShortcutItem, completionHandler: (Boolean) -> Unit) {
        handleShortcut(performActionForShortcutItem)
        completionHandler(true)

    }

}

/**
 * Pending shortcut [JoinConfig] consumed by the HomeScreen once ready. On cold start the
 * delegate may receive the shortcut before the Compose UI and HomeViewmodel exist, so it is
 * parked here for HomeScreen to observe and join instead of polling for the viewmodel.
 */
val pendingShortcutJoinConfig = MutableStateFlow<JoinConfig?>(null)

/** Parses a Quick Action shortcut's room config and posts it to [pendingShortcutJoinConfig]. */
fun handleShortcut(shortcut: UIApplicationShortcutItem) {
    println("HANDLE AMIGO SHORTCUT $shortcut")
    runCatching {
        val joinConfig = Json.decodeFromString<JoinConfig>(shortcut.type)
        pendingShortcutJoinConfig.value = joinConfig
    }
}