package com.chromaticnoob.syncplayutils.utils

import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.source.TrackGroup

class Track {
    var format: Format? = null
    var index: Int = -100
    var trackGroup: TrackGroup? = null
    var trackType: Int? = null
    var selected: Boolean = false

}