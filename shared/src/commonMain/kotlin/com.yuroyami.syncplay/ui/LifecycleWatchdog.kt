package com.yuroyami.syncplay.ui

interface LifecycleWatchdog {

    /**
     * Called when the component is visible and interactive.
     * @author Equivalent to viewDidAppear on iOS.
     */
    fun onResume()

    /**
     * Called when the component is no longer visible.
     * @author Equivalent to viewDidDisappear on iOS.
     */
    fun onStop()

    /**
     * Called when the component is first created.
     * @author Equivalent to viewDidLoad on iOS.
     */
    fun onCreate()

    /**
     * Called when the component is becoming visible.
     * @author Equivalent to viewWillAppear on iOS.
     */
    fun onStart()

    /**
     * Called when the component is no longer visible but still partially visible.
     * @author Equivalent to viewWillDisappear on iOS.
     */
    fun onPause()

}
