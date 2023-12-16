package com.yuroyami.syncplay.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuroyami.syncplay.models.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

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
abstract class BasePlayer {

    abstract val engine: ENGINE

    val playerScopeMain = CoroutineScope(Dispatchers.Main)
    val playerScopeIO = CoroutineScope(Dispatchers.IO)

    /** Called when the player is to be initialized */
    abstract fun initialize()

    /** Returns whether the current player has any media loaded */
    abstract fun hasMedia(): Boolean

    /** Returns whether the current player is in play state (unpaused) */
    abstract fun isPlaying(): Boolean

    /** Called when the player ought to analyze the tracks of the currently loaded media */
    abstract fun analyzeTracks(mediafile: MediaFile)

    abstract fun selectTrack(type: TRACKTYPE, index: Int)

    abstract fun reapplyTrackChoices()

    /** Loads an external sub given the [uri] */
    abstract fun loadExternalSub(uri: String)

    /** Loads a media located at [uri] */
    abstract fun injectVideo(uri: String? = null, isUrl: Boolean = false)

    abstract fun pause()

    abstract fun play()

    abstract fun isSeekable(): Boolean

    abstract fun seekTo(toPositionMs: Long)

    abstract fun currentPositionMs(): Long

    abstract fun switchAspectRatio(): String

    abstract fun collectInfoLocal(mediafile: MediaFile)

    abstract fun collectInfoURL(mediafile: MediaFile)

    abstract fun changeSubtitleSize(newSize: Int)

    @Composable
    abstract fun VideoPlayer(modifier: Modifier)


}