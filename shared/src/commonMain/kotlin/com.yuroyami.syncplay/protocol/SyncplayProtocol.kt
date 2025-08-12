package com.yuroyami.syncplay.protocol

import com.yuroyami.syncplay.viewmodel.SyncplayViewmodel
import kotlinx.coroutines.flow.MutableStateFlow

abstract class SyncplayProtocol(
    val viewmodel: SyncplayViewmodel
) {
    /** Protocol-exclusive variables - should never change these initial values **/
    var serverIgnFly: Int = 0
    var clientIgnFly: Int = 0

    val rewindThreshold = 12L /* This is as per official Syncplay, shouldn't be subject to change */

    /** Global server values that the user often needs to adjust to (see handleState) */
    var globalPaused: Boolean = true //Latest server paused state
    var globalPositionMs: Double = 0.0 //Latest server video position



    /** Coroutine scopes and dispatchers */
}
