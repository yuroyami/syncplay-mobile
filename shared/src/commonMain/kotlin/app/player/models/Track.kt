package app.player.models

import app.player.PlayerImpl

/*****************************************************************************************
 * Track wrapper class. It encapsulates all info we need about a track in a track group  *
 *****************************************************************************************/
abstract class Track {
    /** Name of the track */
    abstract val name: String

    /** Corresponds to either subtitle track or audio track type **/
    abstract val type: PlayerImpl.TrackType?

    /** The index of the format (track) **/
    abstract val index: Int

    /** The current status of the track **/
    abstract val selected: Boolean

    /**
     * What the platform says this track is for, when it says anything at all. A viewer who needs
     * captions for the deaf and hard of hearing, or an audio description, cannot tell those apart
     * from an ordinary track by name alone: many files label both "English".
     */
    open val trait: TrackTrait? get() = null

    /**
     * The language tag the file states for this track, when the engine exposes one on its own.
     * Engines that only put the language inside the display name answer null and rely on their
     * own native preference handling instead.
     */
    open val language: String? get() = null
}

/** The kinds of track a platform marks as serving a purpose beyond a plain language choice. */
enum class TrackTrait {
    /** Captions for the deaf and hard of hearing, or an audio description of the picture. */
    ACCESSIBILITY,

    /** A subtitle the file asks to be shown even when subtitles are otherwise off. */
    FORCED,
}