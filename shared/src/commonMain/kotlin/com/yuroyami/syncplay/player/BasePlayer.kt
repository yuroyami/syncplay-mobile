package com.yuroyami.syncplay.player

import com.yuroyami.syncplay.models.MediaFile

/** This is an interface that wraps the needed player functionality for Syncplay.
 *
 * Currently available players for Android are:
 * 1- ExoPlayer (by Google) which is known to be the most stable and versatile
 * 2- MPV (most powerful and plays most formats), mildly stable
 *
 * Currently available players for iOS are:
 * 3- AVPlayer
 *
 * This interface will be implemented by one of the players mentioned above, and will delegate
 * all the necessary functionality, in a platform-agnostic manner.
 */
interface BasePlayer {

    /** Called when the player is to be initialized */
    fun initialize(extra: Any)

    /** Returns whether the current player has any media loaded */
    fun hasMedia(): Boolean

    /** Returns whether the current player is in play state (unpaused) */
    fun isPlaying(): Boolean

    /** Called when the player ought to analyze the tracks of the currently loaded media */
    fun analyzeTracks(mediafile: MediaFile)

    fun selectTrack(type: Int, index: Int)

    fun reapplyTrackChoices()

    /** Loads an external sub given the [uri] */
    fun loadExternalSub(uri: String)

    /** Loads a media located at [uri] */
    fun injectVideo(uri: String? = null, isUrl: Boolean = false)

    fun pause()

    fun play()

    fun isSeekable(): Boolean

    fun seekTo(toPositionMs: Long)

    fun currentPositionMs(): Long

    fun switchAspectRatio(): String

}