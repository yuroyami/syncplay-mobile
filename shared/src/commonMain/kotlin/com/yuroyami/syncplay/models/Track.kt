package com.yuroyami.syncplay.models

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.yuroyami.syncplay.player.BasePlayer

/*****************************************************************************************
 * Track wrapper class. It encapsulates all info we need about a track in a track group  *
 *****************************************************************************************/
interface Track {
    /** Name of the track */
    val name: String

    /** Corresponds to either subtitle track or audio track type **/
    val type: BasePlayer.TRACKTYPE?

    /** The index of the format (track) **/
    val index: Int

    /** The current status of the track **/
    val selected: MutableState<Boolean> }