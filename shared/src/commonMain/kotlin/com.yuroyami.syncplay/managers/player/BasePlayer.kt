package com.yuroyami.syncplay.managers.player

import androidx.annotation.CallSuper
import androidx.annotation.UiThread
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eygraber.uri.Uri
import com.yuroyami.syncplay.managers.settings.ExtraSettingBundle
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.utils.getFileSize
import com.yuroyami.syncplay.utils.sha256
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_selected_sub
import syncplaymobile.shared.generated.resources.room_selected_sub_error
import syncplaymobile.shared.generated.resources.room_selected_vid
import syncplaymobile.shared.generated.resources.room_sub_error_load_vid_first
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
    val viewmodel: RoomViewmodel,
    val engine: PlayerEngine
) {
    val playerManager: PlayerManager = viewmodel.playerManager

    enum class TRACKTYPE {
        AUDIO, SUBTITLE
    }

    private val playerSupervisorJob = SupervisorJob()
    val playerScopeMain = CoroutineScope(Dispatchers.Main + playerSupervisorJob)
    val playerScopeIO = CoroutineScope(Dispatchers.IO + playerSupervisorJob)

    abstract val canChangeAspectRatio: Boolean
    abstract val supportsChapters: Boolean

    var isInitialized: Boolean = false

    /** Called when the player is to be initialized */
    @UiThread
    abstract fun initialize()

    /** Called when the player needs to be destroyed */
    abstract suspend fun destroy()

    /** Called by Compose to check whether there are any extra settings for this player */
    abstract suspend fun configurableSettings(): ExtraSettingBundle?

    /** Returns whether the current player has any media loaded */
    abstract suspend fun hasMedia(): Boolean

    /** Returns whether the current player is in play state (unpaused) */
    abstract suspend fun isPlaying(): Boolean

    /** Called when the player ought to analyze the tracks of the currently loaded media */
    abstract suspend fun analyzeTracks(mediafile: MediaFile)

    abstract suspend fun selectTrack(track: Track?, type: TRACKTYPE)

    abstract suspend fun analyzeChapters(mediafile: MediaFile)
    abstract suspend fun jumpToChapter(chapter: Chapter)
    abstract suspend fun skipChapter()

    abstract suspend fun reapplyTrackChoices()

    /** Loads an external sub given the [uri] */
    suspend fun loadExternalSub(uri: String) {
        if (!isInitialized) return

        if (hasMedia()) {
            val filename = getFileName(uri = uri).toString()
            val extension = filename.substring(filename.length - 4).lowercase()

            if (isValidSubtitleFile(extension)) {
                loadExternalSubImpl(uri, extension)

                viewmodel.osdManager.dispatchOSD {
                    getString(Res.string.room_selected_sub, filename)
                }
            } else {
                viewmodel.osdManager.dispatchOSD {
                    getString(Res.string.room_selected_sub_error)
                }
            }
        } else {
            viewmodel.osdManager.dispatchOSD {
                getString(Res.string.room_sub_error_load_vid_first)
            }
        }
    }

    abstract suspend fun loadExternalSubImpl(uri: String, extension: String)

    private fun isValidSubtitleFile(extension: String) =
        listOf("srt", "ass", "ssa", "ttml", "vtt").any { it in extension.lowercase() }


    /** Loads a media located at [uri] */
    suspend fun injectVideo(uri: String? = null, isUrl: Boolean = false) {
        if (!isInitialized) return

        withContext(Dispatchers.Main) {
            /* Creating a media file from the selected file */
            val newMediaFile = MediaFile()
            if (uri != null || viewmodel.media == null) {
                newMediaFile.uri = uri

                /* Obtaining info from it (size and name) */
                if (isUrl) {
                    newMediaFile.url = uri
                    collectInfoURL(newMediaFile)
                } else {
                    collectInfoLocal(newMediaFile)
                }
            }
            try {
                injectVideoImpl(newMediaFile, isUrl)
            } catch (e: Exception) {
                /* If, for some reason, the video didn't wanna load */
                e.printStackTrace()
                viewmodel.osdManager.dispatchOSD { "There was a problem loading this file." }
            }

            playerManager.media.value = newMediaFile

            /* Finally, show a a toast to the user that the media file has been added */
            viewmodel.osdManager.dispatchOSD {
                getString(Res.string.room_selected_vid, "${viewmodel.media?.fileName}")
            }
        }
    }

    abstract suspend fun injectVideoImpl(media: MediaFile, isUrl: Boolean)

    abstract suspend fun pause()

    abstract suspend fun play()

    abstract suspend fun isSeekable(): Boolean

    @UiThread
    @CallSuper
    open fun seekTo(toPositionMs: Long) {
        if (viewmodel.lifecycleManager.isInBackground) return
    }

    @UiThread
    abstract fun currentPositionMs(): Long

    abstract suspend fun switchAspectRatio(): String

    abstract suspend fun changeSubtitleSize(newSize: Int)

    @Composable
    abstract fun VideoPlayer(modifier: Modifier)

    abstract fun getMaxVolume(): Int
    abstract fun getCurrentVolume(): Int
    abstract fun changeCurrentVolume(v: Int)

    fun onPlaybackEnded() {
        if (!isInitialized) return

        if (!viewmodel.isSoloMode) {
            if (viewmodel.sessionManager.session.sharedPlaylist.isEmpty()) return
            val currentIndex = viewmodel.sessionManager.session.spIndex.intValue
            val playlistSize = viewmodel.sessionManager.session.sharedPlaylist.size

            val next = if (playlistSize == currentIndex + 1) 0 else currentIndex + 1
            viewmodel.playlistManager.sendPlaylistSelection(next)
        }
    }

    fun collectInfoURL(media: MediaFile) {
        with(media) {
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

    suspend fun collectInfoLocal(mediafile: MediaFile) {
        withContext(Dispatchers.IO) {
            with(mediafile) {
                /** Using MiscUtils **/
                fileName = getFileName(uri!!)!!
                fileSize = getFileSize(uri!!).toString()

                /** Hashing name and size in case they're used **/
                fileNameHashed = sha256(fileName).toHexString(HexFormat.UpperCase)
                fileSizeHashed = sha256(fileSize).toHexString(HexFormat.UpperCase)
            }
        }
    }

    abstract val trackerJobInterval: Duration
    private val playerTrackerJob by lazy {
        playerScopeMain.launch {
            while (isActive) {
                if (isSeekable()) {
                    val pos = currentPositionMs()
                    playerManager.timeCurrentMillis.value = pos
                    if (!viewmodel.isSoloMode) {
                        //viewmodel.protocolManager.globalPositionMs = pos.toDouble()
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