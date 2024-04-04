package com.yuroyami.syncplay.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eygraber.uri.Uri
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.utils.sha256
import com.yuroyami.syncplay.utils.toHex
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** This is an interface that wraps the needed player functionality for Syncplay.
 *
 * Currently available players for Android are:
 * 1- ExoPlayer (by Google) which is known to be the most stable but doesn't support a lot of formats
 * 2- MPV (most powerful and plays most formats), mildly stable
 * 3- VLC (supports the widest range of formats even QuickTime and Xvid, powerful yet very unstable)
 *
 * Currently available players for iOS are:
 * 1- AVPlayer
 *
 * This interface will be implemented by one of the players mentioned above, and will delegate
 * all the necessary functionality, in a platform-agnostic manner.
 */
abstract class BasePlayer {

    abstract val engine: ENGINE

    enum class ENGINE {
        ANDROID_EXOPLAYER,
        ANDROID_MPV,
        ANDROID_VLC,
        IOS_AVPLAYER,
        IOS_VLC;

        fun getNextPlayer(): ENGINE {
            return when (this) {
                ANDROID_EXOPLAYER -> ANDROID_MPV
                ANDROID_MPV -> ANDROID_VLC
                ANDROID_VLC -> ANDROID_EXOPLAYER
                IOS_AVPLAYER -> IOS_VLC
                IOS_VLC -> IOS_AVPLAYER
            }
        }
    }

    enum class TRACKTYPE {
        AUDIO , SUBTITLE
    }

    val playerScopeMain = CoroutineScope(Dispatchers.Main)
    val playerScopeIO = CoroutineScope(Dispatchers.IO)

    abstract val canChangeAspectRatio: Boolean
    abstract val supportsChapters: Boolean

    /** Called when the player is to be initialized */
    abstract fun initialize()

    /** Called when the player needs to be destroyed */
    abstract fun destroy()

    /** Returns whether the current player has any media loaded */
    abstract fun hasMedia(): Boolean

    /** Returns whether the current player is in play state (unpaused) */
    abstract fun isPlaying(): Boolean

    /** Called when the player ought to analyze the tracks of the currently loaded media */
    abstract fun analyzeTracks(mediafile: MediaFile)

    abstract fun selectTrack(type: TRACKTYPE, index: Int)

    abstract fun analyzeChapters(mediafile: MediaFile)
    abstract fun jumpToChapter(chapter: Chapter)
    abstract fun skipChapter()

    abstract fun reapplyTrackChoices()

    /** Loads an external sub given the [uri] */
    abstract fun loadExternalSub(uri: String)

    /** Loads a media located at [uri] */
    abstract fun injectVideo(uri: String? = null, isUrl: Boolean = false)

    abstract fun pause()

    abstract fun play()

    abstract fun isSeekable(): Boolean

    open fun seekTo(toPositionMs: Long) {
        if (viewmodel?.background == true) return;
    }

    abstract fun currentPositionMs(): Long

    abstract fun switchAspectRatio(): String

    abstract fun collectInfoLocal(mediafile: MediaFile)

    abstract fun changeSubtitleSize(newSize: Int)

    @Composable
    abstract fun VideoPlayer(modifier: Modifier)

    fun collectInfoURL(media: MediaFile) {
        with (media) {
            try {
                /** Using Ktor's built-in URL support **/
                fileName = Uri.parseOrNull(url!!)?.pathSegments?.last() ?: "Undefined"
                fileSize = 0L.toString()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            /** Hashing name and size in case they're used **/
            fileNameHashed = sha256(fileName).toHex()
            fileSizeHashed = sha256(fileSize).toHex()
        }
    }

}