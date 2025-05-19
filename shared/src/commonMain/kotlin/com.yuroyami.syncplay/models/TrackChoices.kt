package com.yuroyami.syncplay.models

//TODO USE A SEALED CLASS INSTEAD FOR TYPESAFETY
class TrackChoices {
    /* Exo */
    var lastAudioOverride: Any? = null //TYPE: TrackSelectionOverride
    var lastSubtitleOverride: Any? = null

    /* mpv */
    var audioSelectionIndexMpv: Int? = null
    var subtitleSelectionIndexMpv: Int? = null

    /* vlc */
    var audioSelectionIdVlc: String? = null
    var subtitleSelectionIdVlc: String? = null
}