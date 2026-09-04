package app.player.models

import app.player.PlayerImpl.TrackType

/**
 * A track the user picked, kept so a reload or a return from the background can restore it.
 *
 * Engines address their tracks in different ways, so the choice carries the engine's own handle.
 * [Off] is a real choice and not the absence of one: it says the user switched the track off, which
 * a null choice (never picked anything) must not be confused with.
 */
sealed interface TrackChoice {

    /** The user switched this track off. */
    data object Off : TrackChoice

    /** mpv (`sid`/`aid`), VLCKit and AVPlayer all address a track by its position in the list. */
    data class ByIndex(val index: Int) : TrackChoice

    /**
     * ExoPlayer answers with a `TrackSelectionOverride`, a type common code cannot name, so it
     * travels opaquely and the Android engine casts it back.
     */
    data class ByOverride(val override: Any) : TrackChoice
}

/** The audio and subtitle picks for the media in play. Cleared with the player. */
class TrackChoices {

    var audio: TrackChoice? = null
    var subtitle: TrackChoice? = null

    operator fun get(type: TrackType): TrackChoice? = when (type) {
        TrackType.AUDIO -> audio
        TrackType.SUBTITLE -> subtitle
    }

    operator fun set(type: TrackType, choice: TrackChoice?) {
        when (type) {
            TrackType.AUDIO -> audio = choice
            TrackType.SUBTITLE -> subtitle = choice
        }
    }

    /** Records what [selectTrack] was just asked for: an index, or Off when the track was null. */
    fun remember(type: TrackType, track: Track?) {
        this[type] = track?.let { TrackChoice.ByIndex(it.index) } ?: TrackChoice.Off
    }
}
