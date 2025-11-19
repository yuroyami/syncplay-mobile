package com.yuroyami.syncplay.models

import com.yuroyami.syncplay.managers.player.BasePlayer

/*****************************************************************************************
 * Track wrapper class. It encapsulates all info we need about a track in a track group  *
 *****************************************************************************************/
abstract class Track {
    /** Name of the track */
    abstract val name: String

    /** Corresponds to either subtitle track or audio track type **/
    abstract val type: BasePlayer.TrackType?

    /** The index of the format (track) **/
    abstract val index: Int

    /** The current status of the track **/
    abstract val selected: Boolean
}