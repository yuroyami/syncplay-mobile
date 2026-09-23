package app.player.models

import app.player.PlayerImpl

/** One audio, subtitle or video track of a media file, as an engine reports it. */
abstract class Track {
    abstract val name: String

    /** The track type (audio, subtitle or video), or null when it is unknown. */
    abstract val type: PlayerImpl.TrackType?

    /** The number that the engine uses to select this track. */
    abstract val index: Int

    /** True while this track is selected. */
    abstract val selected: Boolean

    /**
     * What the platform says this track is for, or null when it says nothing. A viewer who needs
     * captions for the deaf and hard of hearing, or an audio description, cannot tell those apart
     * from an ordinary track by name alone: many files label both "English".
     */
    open val trait: TrackTrait? get() = null

    /**
     * The language tag that the file states for this track, when the engine exposes one on its
     * own. An engine that puts the language only inside the display name returns null and keeps
     * its own native language handling.
     */
    open val language: String? get() = null
    open val channelCount: Int? get() = null
    open val channelLayout: String? get() = null
    open val codec: String? get() = null
    open val videoDescription: String? get() = null
}

/** The kinds of track a platform marks as serving a purpose beyond a plain language choice. */
enum class TrackTrait {
    /** Captions for the deaf and hard of hearing, or an audio description of the picture. */
    ACCESSIBILITY,

    /** A subtitle the file asks to be shown even when subtitles are otherwise off. */
    FORCED,
}