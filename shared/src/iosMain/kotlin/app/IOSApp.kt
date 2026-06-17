@file:Suppress("unused", "FunctionName")

package app

import androidx.compose.ui.window.ComposeUIViewController
import app.home.HomeViewmodel
import app.room.RoomViewmodel
import app.utils.platformCallback
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

/**
 * Creates the root UIViewController for the Syncplay iOS application.
 *
 * A custom parent view controller hosts the Compose UI as a child. The parent-child pattern
 * is required because ComposeUIViewControllerDelegate emits lifecycle events unreliably;
 * overriding the parent's lifecycle methods directly is the workaround.
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
    platformCallback = ApplePlatformCallback

    val parentController = object : UIViewController(nibName = null, bundle = null) {
        /** Hosts the Compose UI as a child VC pinned to fill the parent. */
        override fun viewDidLoad() {
            super.viewDidLoad()

            val composeController = ComposeUIViewController {
                AdamScreen(
                    onGlobalViewmodel = {
                        globalViewmodel = it
                    }
                )
            }

            addChildViewController(composeController)
            view.addSubview(composeController.view)
            composeController.didMoveToParentViewController(this)

            composeController.view.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activateConstraints(listOf(
                composeController.view.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
                composeController.view.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
                composeController.view.topAnchor.constraintEqualToAnchor(view.topAnchor),
                composeController.view.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor)
            ))

            roomViewmodel?.uiState?.onLifecycleCreate()
        }

        /** viewDidAppear → onResume. */
        override fun viewDidAppear(animated: Boolean) {
            super.viewDidAppear(animated)

            roomViewmodel?.uiState?.onLifecycleResume()
        }

        /** viewDidDisappear → onStop. */
        override fun viewDidDisappear(animated: Boolean) {
            super.viewDidDisappear(animated)

            roomViewmodel?.uiState?.onLifecycleStop()
        }

        /** viewWillAppear → onStart. */
        override fun viewWillAppear(animated: Boolean) {
            super.viewWillAppear(animated)

            roomViewmodel?.uiState?.onLifecycleStart()
        }

        /** viewWillDisappear → onPause. */
        override fun viewWillDisappear(animated: Boolean) {
            super.viewWillDisappear(animated)
            roomViewmodel?.uiState?.onLifecyclePause()
        }
    }

    return parentController
}