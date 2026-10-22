package app

import app.utils.platformCallback
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
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
 * UIApplicationDelegate for what the Compose layer cannot handle: the orientation mask. Invite
 * links and Quick Actions arrive through the scene instead, see IncomingJoins.kt.
 */
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
}
