@file:Suppress("unused", "FunctionName")

package app

import androidx.compose.ui.window.ComposeUIViewController
import app.home.HomeViewmodel
import app.room.RoomViewmodel
import app.utils.flushLogs
import app.utils.loggy
import app.utils.platformCallback
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

lateinit var globalViewmodel: SyncplayViewmodel

val homeViewmodel: HomeViewmodel?
    get() = if (::globalViewmodel.isInitialized) globalViewmodel.homeWeakRef?.get() else null

val roomViewmodel: RoomViewmodel?
    get() = if (::globalViewmodel.isInitialized) globalViewmodel.roomWeakRef?.get() else null

/**
 * Creates the root UIViewController of the iOS app.
 *
 * A plain parent view controller hosts the Compose UI as a child. The parent is needed because
 * ComposeUIViewControllerDelegate reports lifecycle events unreliably, so the parent overrides
 * the lifecycle methods itself.
 *
 * ## Architecture
 * ```
 * ParentViewController (this function)
 *   └─ ComposeUIViewController (Compose content)
 *        └─ AdamScreen (main app UI)
 * ```
 *
 * ## Lifecycle Events
 * The parent forwards iOS lifecycle events to the current room (the group of people watching
 * together) through its `RoomUiStateManager`:
 * - `viewDidLoad` → onCreate
 * - `viewWillAppear` → onStart
 * - `viewDidAppear` → onResume
 * - `viewWillDisappear` → onPause
 * - `viewDidDisappear` → onStop
 */
fun SyncplayController(): UIViewController {
    platformCallback = ApplePlatformCallback
    observeAppBackgrounding()
    installCrashHook()

    val parentController = object : UIViewController(nibName = null, bundle = null) {
        /** Adds the Compose UI as a child view controller, pinned to fill the parent. */
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

        override fun viewDidAppear(animated: Boolean) {
            super.viewDidAppear(animated)

            roomViewmodel?.uiState?.onLifecycleResume()
        }

        override fun viewDidDisappear(animated: Boolean) {
            super.viewDidDisappear(animated)

            roomViewmodel?.uiState?.onLifecycleStop()
        }

        override fun viewWillAppear(animated: Boolean) {
            super.viewWillAppear(animated)

            roomViewmodel?.uiState?.onLifecycleStart()
        }

        override fun viewWillDisappear(animated: Boolean) {
            super.viewWillDisappear(animated)
            roomViewmodel?.uiState?.onLifecyclePause()
        }
    }

    return parentController
}

private var backgroundObserversInstalled = false

/**
 * Installs a hook that writes an uncaught Kotlin exception to the log file before the process
 * dies.
 *
 * The log writer is asynchronous, so the hook flushes it before it returns. Otherwise the trace
 * can still sit in the queue when the process goes away. The hook also ends the process itself:
 * with a hook installed, the runtime hands the exception to the hook and returns, so the app would
 * carry on in whatever state the crash left it in.
 */
@OptIn(ExperimentalNativeApi::class)
private fun installCrashHook() {
    setUnhandledExceptionHook { throwable ->
        loggy("Uncaught Kotlin exception: ${throwable.stackTraceToString()}")
        // Wait a bounded time for the flush. A crash report is worth a second and a half.
        runCatching { runBlocking { withTimeout(CRASH_LOG_FLUSH_TIMEOUT) { flushLogs() } } }
        terminateWithUnhandledException(throwable)
    }
}

/** Long enough for the writer to save a stack trace, short enough not to hang a dying process. */
private val CRASH_LOG_FLUSH_TIMEOUT = 1500.milliseconds

/**
 * Sends the app's moves to the background and back to the room lifecycle, like Android's ON_STOP
 * and ON_START. The root view controller does not disappear when the app goes to the background,
 * so the view-controller hooks above never see that move. The application notifications do.
 */
private fun observeAppBackgrounding() {
    if (backgroundObserversInstalled) return
    backgroundObserversInstalled = true
    val center = NSNotificationCenter.defaultCenter
    val queue = NSOperationQueue.mainQueue
    center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, queue) { _ ->
        roomViewmodel?.uiState?.onLifecycleStop()
    }
    center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, queue) { _ ->
        roomViewmodel?.uiState?.onLifecycleStart()
    }
}