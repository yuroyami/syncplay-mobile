package com.yuroyami.syncplay.managers

import com.yuroyami.syncplay.logic.AbstractManager
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import kotlin.concurrent.Volatile

/**
 * Manages lifecycle-related behavior for the app across platforms (Android Activity or iOS ViewController).
 *
 * This manager is **not** a generic lifecycle binding — it exists solely to perform app-specific
 * actions when the UI component (Activity on Android / ViewController on iOS) changes state.
 *
 * Specifically:
 * - Tracks whether the app has gone into the background via [background].
 * - Pauses playback when the component stops, unless in Picture-in-Picture mode.
 * - Provides hooks equivalent to iOS lifecycle events (`viewDidLoad`, `viewWillAppear`, etc.)
 *   so that shared code can run consistently across platforms.
 *
 * ## Lifecycle mapping:
 * - [onCreate] → iOS `viewDidLoad` / Android `onCreate`
 * - [onStart] → iOS `viewWillAppear` / Android `onStart`
 * - [onResume] → iOS `viewDidAppear` / Android `onResume`
 * - [onPause] → iOS `viewWillDisappear` / Android `onPause`
 * - [onStop] → iOS `viewDidDisappear` / Android `onStop`
 *
 * @property background Atomic boolean indicating whether the app is considered in the background.
 */
class LifecycleManager(viewmodel: SyncplayViewmodel) : AbstractManager(viewmodel) {

    /** Whether the app is currently in the background (true after [onStop] unless in PiP mode). */
    @Volatile
    var background = false

    /** Called when the component (activity or viewcontroller) is first created. */
    fun onCreate() {
        // Nothing to do
    }

    /** Called when the component is becoming visible. */
    fun onStart() {
        background = false
    }

    /** Called when the component is visible and interactive. */
    fun onResume() {
        background = false
    }

    /** Called when the component is no longer interactable but still partially visible. */
    fun onPause() {
        // Nothing to do
    }

    /**
     * Called when the component is no longer visible and interactable.
     * Pauses playback unless in Picture-in-Picture mode.
     */
    fun onStop() {
        if (!viewmodel.uiManager.hasEnteredPipMode.value) {
            background = true

            onMainThread {
                viewmodel.playerManager.player?.pause()
            }
        }
    }

    val isInBackground: Boolean
        get() = background
}
