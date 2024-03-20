package com.yuroyami.syncplay.models

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.yuroyami.syncplay.player.BasePlayer

/*****************************************************************************************
 * Track wrapper class. It encapsulates all info we need about a track in a track group  *
 *****************************************************************************************/

data class Track(
    val name: String,

    /** The index of the format (track) **/
    val index: Int = -100,

    /** Corresponds to either subtitle track or audio track type **/
    val trackType: BasePlayer.TRACKTYPE? = null,

    /** EXO: The corresponding format (ExoPlayer calls track a format for some reason) **/
    val format_EXO_ONLY: Any? = null,

    /** EXO:  The trackgroup in which the track/format exists **/
    val trackGroup_EXO_ONLY: Any? = null
) {
    /** The current status of the track **/
    var selected: MutableState<Boolean> = mutableStateOf(false)
}