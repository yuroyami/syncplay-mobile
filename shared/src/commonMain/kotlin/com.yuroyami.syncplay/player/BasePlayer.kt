package com.yuroyami.syncplay.player

import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eygraber.uri.Uri
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.settings.ExtraSettingBundle
import com.yuroyami.syncplay.utils.CommonUtils.sha256
import com.yuroyami.syncplay.viewmodel.SyncplayViewmodel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

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
abstract class BasePlayer(
    val viewmodel: SyncplayViewmodel,
    val engine: PlayerEngine
) {

    enum class TRACKTYPE {
        AUDIO , SUBTITLE
    }

    private val playerSupervisorJob = SupervisorJob()
    val playerScopeMain = CoroutineScope(Dispatchers.Main + playerSupervisorJob)
    val playerScopeIO = CoroutineScope(Dispatchers.IO + playerSupervisorJob)

    abstract val canChangeAspectRatio: Boolean
    abstract val supportsChapters: Boolean

    /** Called when the player is to be initialized */
    abstract fun initialize()

    /** Called when the player needs to be destroyed */
    abstract fun destroy()

    /** Called by Compose to check whether there are any extra settings for this player */
    abstract fun configurableSettings(): ExtraSettingBundle?

    /** Returns whether the current player has any media loaded */
    abstract fun hasMedia(): Boolean

    /** Returns whether the current player is in play state (unpaused) */
    abstract fun isPlaying(): Boolean

    /** Called when the player ought to analyze the tracks of the currently loaded media */
    abstract suspend fun analyzeTracks(mediafile: MediaFile)

    abstract fun selectTrack(track: Track?, type: TRACKTYPE)

    abstract suspend fun analyzeChapters(mediafile: MediaFile)
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

    @CallSuper
    open fun seekTo(toPositionMs: Long) {
        if (viewmodel.lifecycleManager.isInBackground) return
    }

    abstract fun currentPositionMs(): Long

    abstract suspend fun switchAspectRatio(): String

    abstract fun collectInfoLocal(mediafile: MediaFile)

    abstract fun changeSubtitleSize(newSize: Int)

    @Composable
    abstract fun VideoPlayer(modifier: Modifier)

    abstract fun getMaxVolume(): Int
    abstract fun getCurrentVolume(): Int
    abstract fun changeCurrentVolume(v: Int)

    fun onPlaybackEnded() {
        if (!viewmodel.isSoloMode) {
            if (viewmodel?.p?.session?.sharedPlaylist?.isEmpty() == true) return
            val currentIndex = viewmodel?.p?.session?.spIndex?.intValue ?: return
            val playlistSize = viewmodel?.p?.session?.sharedPlaylist?.size ?: return

            val next = if (playlistSize == currentIndex + 1) 0 else currentIndex + 1
            viewmodel.playlistManager.sendPlaylistSelection(next)

        }
    }

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
            fileNameHashed = sha256(fileName).toHexString(HexFormat.UpperCase)
            fileSizeHashed = sha256(fileSize).toHexString(HexFormat.UpperCase)
        }
    }

    abstract val trackerJobInterval: Duration
    private val playerTrackerJob by lazy {
        playerScopeMain.launch {
            while (isActive) {
                if (isSeekable()) {
                    val pos = currentPositionMs()
                    viewmodel.timeCurrentMs.value = pos
                    if (!viewmodel.isSoloMode) {
                        viewmodel.p.globalPositionMs = pos.toDouble()
                    }
                }
                delay(trackerJobInterval)
            }
        }
    }

    fun startTrackingProgress() {
        // Accessing playerTrackerJob here will start it if it hasn't started yet
        playerTrackerJob
    }

}