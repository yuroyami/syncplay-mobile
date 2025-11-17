@file:Suppress("unused", "FunctionName")
package com.yuroyami.syncplay

import androidx.compose.ui.window.ComposeUIViewController
import com.yuroyami.syncplay.ui.screens.adam.AdamScreen
import com.yuroyami.syncplay.utils.platformCallback
import com.yuroyami.syncplay.viewmodels.HomeViewmodel
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import com.yuroyami.syncplay.viewmodels.SyncplayViewmodel
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

/**
 * Creates the root UIViewController for the Syncplay iOS application.
 *
 * This function creates a custom parent view controller that hosts the Compose UI as a child.
 * The parent-child pattern is used because ComposeUIViewControllerDelegate has buggy lifecycle
 * event handling - events don't get emitted properly. By using a parent view controller, we can
 * override lifecycle methods directly and ensure proper lifecycle management.
 *
 * ## Architecture
 * ```
 * ParentViewController (this function)
 *   └─ ComposeUIViewController (Compose content)
 *        └─ AdamScreen (main app UI)
 * ```
 *
 * ## Lifecycle Events
 * The parent controller intercepts iOS lifecycle events and forwards them to the watchdog:
 * - `viewDidLoad` → onCreate
 * - `viewWillAppear` → onStart
 * - `viewDidAppear` → onResume
 * - `viewWillDisappear` → onPause
 * - `viewDidDisappear` → onStop
 *
 * @return UIViewController configured to host the Syncplay Compose UI with proper lifecycle handling
 */
lateinit var globalViewmodel: SyncplayViewmodel

val homeViewmodel: HomeViewmodel?
    get() = if (::globalViewmodel.isInitialized) globalViewmodel.homeWeakRef?.get() else null

val roomViewmodel: RoomViewmodel?
    get() = if (::globalViewmodel.isInitialized) globalViewmodel.roomWeakRef?.get() else null

fun SyncplayController(): UIViewController {
    // Initialize platform-specific callback handler
    platformCallback = ApplePlatformCallback

    // Create a custom parent view controller
    val parentController = object : UIViewController(nibName = null, bundle = null) {
        /**
         * Called after the controller's view is loaded into memory.
         *
         * Sets up the Compose UI as a child view controller with Auto Layout constraints
         * to fill the entire parent view.
         */
        override fun viewDidLoad() {
            super.viewDidLoad()

            // Create the Compose UI controller as a child
            val composeController = ComposeUIViewController {
                AdamScreen(
                    onGlobalViewmodel = {
                        globalViewmodel = it
                    }
                )
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

            roomViewmodel?.lifecycleManager?.onCreate()
        }

        /**
         * Called after the view has fully appeared on screen.
         * @param animated Whether the appearance was animated
         */
        override fun viewDidAppear(animated: Boolean) {
            super.viewDidAppear(animated)

            roomViewmodel?.lifecycleManager?.onResume()
        }

        /**
         * Called after the view has been fully removed from the screen.
         * @param animated Whether the disappearance was animated
         */
        override fun viewDidDisappear(animated: Boolean) {
            super.viewDidDisappear(animated)

            roomViewmodel?.lifecycleManager?.onStop()
        }

        /**
         * Called just before the view appears on screen.
         *
         * @param animated Whether the appearance will be animated
         */
        override fun viewWillAppear(animated: Boolean) {
            super.viewWillAppear(animated)

            roomViewmodel?.lifecycleManager?.onStart()
        }

        /**
         * Called just before the view is removed from the screen.
         *
         * @param animated Whether the disappearance will be animated
         */
        override fun viewWillDisappear(animated: Boolean) {
            super.viewWillDisappear(animated)
            roomViewmodel?.lifecycleManager?.onPause()
        }
    }

    return parentController
}