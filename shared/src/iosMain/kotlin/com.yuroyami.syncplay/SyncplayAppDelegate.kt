package com.yuroyami.syncplay

import com.yuroyami.syncplay.utils.platformCallback
import platform.AVKit.AVPictureInPictureController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIApplicationLaunchOptionsShortcutItemKey
import platform.UIKit.UIApplicationShortcutItem
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindow
import platform.darwin.NSObject

val delegato = AppleDelegate().also {
    UIApplication.sharedApplication.delegate = it
}

var pipcontroller: AVPictureInPictureController? = null

@Suppress("CONFLICTING_OVERLOADS")
class AppleDelegate : NSObject(), UIApplicationDelegateProtocol {
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

fun handleShortcut(shortcut: UIApplicationShortcutItem) {
    println("HANDLE AMIGO SHORTCUT $shortcut")
    sc = shortcut
    //TODO homeCallback?.onJoin(shortcut.type.toJoinInfo().get())
}