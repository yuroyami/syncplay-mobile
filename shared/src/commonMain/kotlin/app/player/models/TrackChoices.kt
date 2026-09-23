package app.player.models

import app.player.PlayerImpl.TrackType

/**
 * A track that the user picked, kept so that a reload or a return from the background can
 * restore it.
 *
 * Engines address their tracks in different ways, so the choice carries the engine's own handle.
 * [Off] is a real choice: the user switched the track off. A null choice means that the user
 * never picked anything, and the two must not be confused.
 */
sealed interface TrackChoice {

    /** The user switched this track off. */
    data object Off : TrackChoice

    /** One number: mpv's track id (`sid`/`aid`), or the list position in VLCKit and AVPlayer. */
    data class ByIndex(val index: Int) : TrackChoice

    /**
     * ExoPlayer uses a `TrackSelectionOverride`, a type that common code cannot name. So the
     * choice carries it as `Any`, and the Android engine casts it back.
     */
    data class ByOverride(val override: Any) : TrackChoice
}

/**
 * The user's audio, subtitle and video picks. They carry over between files and are cleared with
 * the player.
 */
class TrackChoices {

    var audio: TrackChoice? = null
    var subtitle: TrackChoice? = null
    var video: TrackChoice? = null

    operator fun get(type: TrackType): TrackChoice? = when (type) {
        TrackType.AUDIO -> audio
        TrackType.SUBTITLE -> subtitle
        TrackType.VIDEO -> video
    }

    operator fun set(type: TrackType, choice: TrackChoice?) {
        when (type) {
            TrackType.AUDIO -> audio = choice
            TrackType.SUBTITLE -> subtitle = choice
            TrackType.VIDEO -> video = choice
        }
    }

    /** Records what `selectTrack` was asked for: an index, or [TrackChoice.Off] for a null track. */
    fun remember(type: TrackType, track: Track?) {
        this[type] = track?.let { TrackChoice.ByIndex(it.index) } ?: TrackChoice.Off
    }
}
