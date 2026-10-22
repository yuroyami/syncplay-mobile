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

    /**
     * A pick carried over from the previous file: the track of this file in the same language.
     * An index or an override names a track of one file only, so it never crosses to the next.
     */
    data class ByLanguage(val language: String) : TrackChoice
}

/**
 * The user's audio, subtitle and video picks, cleared with the player. A pick follows the viewer
 * to the next file by its language, see [forNextFile].
 */
class TrackChoices {

    var audio: TrackChoice? = null
    var subtitle: TrackChoice? = null
    var video: TrackChoice? = null

    /** The language of each picked track, so that the pick can follow the viewer to the next file. */
    private val pickedLanguages = mutableMapOf<TrackType, String>()

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
        rememberLanguage(type, track)
    }

    /** Records the language of a picked [track]. An engine that stores its own handle calls this too. */
    fun rememberLanguage(type: TrackType, track: Track?) {
        val language = track?.language?.takeIf { it.isNotBlank() && it != "und" }
        if (language == null) pickedLanguages.remove(type) else pickedLanguages[type] = language
    }

    /**
     * The picks as they apply to the next file. A picked track becomes its language, or nothing
     * when it had none. A track switched off stays off.
     */
    fun forNextFile(): TrackChoices = TrackChoices().also { next ->
        for (type in TrackType.entries) {
            next[type] = when (this[type]) {
                null -> null
                TrackChoice.Off -> TrackChoice.Off
                else -> pickedLanguages[type]?.let { TrackChoice.ByLanguage(it) }
            }
            pickedLanguages[type]?.let { next.pickedLanguages[type] = it }
        }
    }
}
