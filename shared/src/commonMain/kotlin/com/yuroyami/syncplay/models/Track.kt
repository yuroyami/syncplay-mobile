package com.yuroyami.syncplay.models

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/*****************************************************************************************
 * Track wrapper class. It encapsulates all info we need about a track in a track group  *
 *****************************************************************************************/

data class Track(
    val name: String,

    /** The index of the format (track) **/
    val index: Int = -100,

    /** Corresponds to either subtitle track type or audio track type **/
    val trackType: Int? = null,

    /** EXO: The corresponding format (ExoPlayer calls track a format for some reason) **/
    //val format: Format? = null,

    /** EXO:  The trackgroup in which the track/format exists **/
    //val trackGroup: TrackGroup? = null
) {
    /** The current status of the track **/
    var selected: MutableState<Boolean> = mutableStateOf(false)
}