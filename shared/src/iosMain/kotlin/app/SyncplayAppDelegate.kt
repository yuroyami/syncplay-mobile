package app

import app.utils.platformCallback
import app.utils.loggy
import app.home.InviteLink
import app.home.JoinConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import platform.AVKit.AVPictureInPictureController
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIApplicationLaunchOptionsShortcutItemKey
import platform.UIKit.UIApplicationShortcutItem
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindow
import platform.darwin.NSObject

/**
 * The app delegate. Kotlin/Native creates it on first use, which comes after launch, and it then
 * replaces UIApplication's delegate.
 */
val delegato = AppleDelegate().also {
    UIApplication.sharedApplication.delegate = it
}

/**
 * The PiP controller of the AVPlayer engine (the video player built on Apple's AVFoundation).
 * [ApplePlatformCallback.onPictureInPicture] sets it.
 */
var pipcontroller: AVPictureInPictureController? = null

/**
 * UIApplicationDelegate for what the Compose layer cannot handle: the orientation mask, Quick
 * Action shortcuts and invite links.
 */
@Suppress("CONFLICTING_OVERLOADS")
class AppleDelegate : NSObject(), UIApplicationDelegateProtocol {

    /**
     * The allowed orientations, which iOS reads through [application]. Starts as portrait.
     * `EnterRoomMode` sets portrait or landscape in a room (a group of people watching together),
     * and `ExitRoomMode` allows all.
     */
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

    /**
     * Routes a launching Quick Action to [handleShortcut]. UIKit calls this only on the delegate
     * that exists at launch. [delegato] is created later, so UIKit never calls it here.
     */
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

    /** Handles an invite link the same way as a Quick Action. */
    override fun application(app: UIApplication, openURL: NSURL, options: Map<Any?, *>): Boolean {
        val parsed = openURL.absoluteString?.let { InviteLink.parse(it) } ?: return false
        pendingShortcutJoinConfig.value = parsed
        return true
    }

}

/**
 * A [JoinConfig] from a Quick Action or an invite link, waiting for the home screen to join it.
 * The home screen may not exist when the config arrives (for example while the user is in a
 * room), so the config waits here. HomeScreen takes it through `consumePendingShortcut()` once,
 * when the screen appears.
 */
val pendingShortcutJoinConfig = MutableStateFlow<JoinConfig?>(null)

/** Parses a Quick Action shortcut's room config and posts it to [pendingShortcutJoinConfig]. */
fun handleShortcut(shortcut: UIApplicationShortcutItem) {
    // Log only the arrival: the type string is the whole join config, both passwords included.
    loggy("Quick Action shortcut received")
    runCatching {
        val joinConfig = Json.decodeFromString<JoinConfig>(shortcut.type)
        pendingShortcutJoinConfig.value = joinConfig
    }
}