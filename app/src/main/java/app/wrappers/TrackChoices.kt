package app.wrappers

import androidx.media3.common.TrackSelectionOverride

class TrackChoices {
    /* Exo */
    var lastAudioOverride: TrackSelectionOverride? = null
    var lastSubtitleOverride: TrackSelectionOverride? = null

    /* mpv */
    var audioSelectionIndexMpv: Int? = null
    var subtitleSelectionIndexMpv: Int? = null
}