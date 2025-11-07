package com.yuroyami.syncplay

import androidx.compose.ui.window.ComposeUIViewController
import com.yuroyami.syncplay.ui.screens.adam.AdamScreen
import com.yuroyami.syncplay.utils.platformCallback
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController


/** This view controller is a parent to the actual ComposeUIViewController,
 * which means it hosts the actual UIViewController that holds our compose views.
 * We do that because ComposeUIViewControllerDelegate is buggy, lifecycle events
 * do not get emitted properly. So we do this so we override lifecycle events
 * directly in the parent view controller. */

fun SyncplayController(): UIViewController {
    platformCallback = ApplePlatformCallback
    
    // Create a custom parent view controller
    val parentController = object : UIViewController(nibName = null, bundle = null) {
        override fun viewDidLoad() {
            super.viewDidLoad()

            // Create the Compose UI controller as a child
            val composeController = ComposeUIViewController {
                AdamScreen()
            }

            // Add as child view controller
            addChildViewController(composeController)
            view.addSubview(composeController.view)
            composeController.didMoveToParentViewController(this)

            // Setup constraints to fill parent
            composeController.view.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activateConstraints(listOf(
                composeController.view.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
                composeController.view.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
                composeController.view.topAnchor.constraintEqualToAnchor(view.topAnchor),
                composeController.view.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor)
            ))

            //TODO watchdog?.onCreate()
        }

        override fun viewDidAppear(animated: Boolean) {
            super.viewDidAppear(animated)

            //watchdog?.onResume()
        }

        override fun viewDidDisappear(animated: Boolean) {
            super.viewDidDisappear(animated)

            //todo watchdog?.onStop()
        }

        override fun viewWillAppear(animated: Boolean) {
            super.viewWillAppear(animated)

            //watchdog?.onStart()
        }

        override fun viewWillDisappear(animated: Boolean) {
            super.viewWillDisappear(animated)
            //watchdog?.onPause()
        }
    }

    return parentController
}