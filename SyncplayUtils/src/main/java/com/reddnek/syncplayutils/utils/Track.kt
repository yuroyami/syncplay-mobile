package com.reddnek.syncplayutils.utils

import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.source.TrackGroup

/*****************************************************************************************
 * Track wrapper class. It encapsulates all info we need about a track in a track group  *
 *****************************************************************************************/

class Track {

    /** The corresponding format (ExoPlayer calls every track a format for some reason **/
    var format: Format? = null

    /** The index of the format (track) **/
    var index: Int = -100

    /** The trackgroup in which the track/format exists **/
    var trackGroup: TrackGroup? = null

    /** Corresponds to either subtitle track type or audio track type **/
    var trackType: Int? = null

    /** The current status of the track **/
    var selected: Boolean = false
}