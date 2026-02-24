package com.yuroyami.syncplay.managers.player

import androidx.annotation.CallSuper
import androidx.annotation.UiThread
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuroyami.syncplay.managers.preferences.Preferences.SUBTITLE_SIZE
import com.yuroyami.syncplay.managers.preferences.value
import com.yuroyami.syncplay.managers.protocol.creator.PacketOut
import com.yuroyami.syncplay.managers.settings.SettingCategory
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.MediaFile.Companion.mediaFromFile
import com.yuroyami.syncplay.models.MediaFile.Companion.mediaFromUrl
import com.yuroyami.syncplay.models.MediaFileLocation
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.utils.GlobalPlayerSession
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import io.github.vinceglb.filekit.PlatformFile
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
import syncplaymobile.shared.generated.resources.room_msg_problem_loading_file
import syncplaymobile.shared.generated.resources.room_selected_sub
import syncplaymobile.shared.generated.resources.room_selected_sub_error
import syncplaymobile.shared.generated.resources.room_selected_vid
import syncplaymobile.shared.generated.resources.room_sub_error_load_vid_first
import kotlin.time.Duration

/** The actual platform-agnostic interface for video/audio playback in Syncplay.
 * Engines: ExoPlayer/MPV/VLC (Android), AVPlayer/VLC (iOS)*/
abstract class PlayerImpl(
    val viewmodel: RoomViewmodel,
    val engine: VideoEngine
) {
    val videoEngineManager: VideoEngineManager = viewmodel.videoEngineManager

    /** MediaSession for system notification and external playback control. */
    val session: GlobalPlayerSession? = null

    enum class TrackType {
        AUDIO, SUBTITLE
    }

    private val playerSupervisorJob = SupervisorJob()
    val playerScopeMain = CoroutineScope(Dispatchers.Main + playerSupervisorJob)
    val playerScopeIO = CoroutineScope(Dispatchers.IO + playerSupervisorJob)

    //TODO
    open val canChangeAspectRatio: Boolean = true
    abstract val supportsChapters: Boolean
    open val supportsPictureInPicture: Boolean = true
    var isInitialized: Boolean = false

    @UiThread
    abstract fun initialize()

    abstract suspend fun destroy()

    abstract fun initMediaSession(): GlobalPlayerSession

    abstract fun finalizeMediaSession()

    abstract suspend fun configurableSettings(): SettingCategory?

    abstract suspend fun hasMedia(): Boolean

    abstract suspend fun isPlaying(): Boolean

    abstract suspend fun analyzeTracks(mediafile: MediaFile)

    abstract suspend fun selectTrack(track: Track?, type: TrackType)

    abstract suspend fun analyzeChapters(mediafile: MediaFile)

    @CallSuper
    open suspend fun jumpToChapter(chapter: Chapter) {
        if (!supportsChapters) return
        val currentPos = currentPositionMs()

        if (!viewmodel.isSoloMode) {
            viewmodel.actionManager.sendSeek(
                oldPosms = currentPos,
                newPosMs = chapter.timeOffsetMillis
            )
        }
    }

    fun skipChapter() {
        if (!supportsChapters) return

        val currentMs = currentPositionMs()

        viewmodel.media?.chapters
            ?.filter { it.timeOffsetMillis > currentMs }
            ?.minByOrNull { it.timeOffsetMillis }
            ?.let { nextChapter ->
                viewmodel.player.seekTo(nextChapter.timeOffsetMillis)

                if (!viewmodel.isSoloMode) {
                    viewmodel.actionManager.sendSeek(
                        oldPosms = currentMs,
                        newPosMs = nextChapter.timeOffsetMillis
                    )
                }
            }
    }

    abstract suspend fun reapplyTrackChoices()

    suspend fun loadExternalSub(uri: PlatformFile) {
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

    abstract suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String)

    private fun isValidSubtitleFile(extension: String) =
        listOf("srt", "ass", "ssa", "ttml", "vtt").any { it in extension.lowercase() }


    companion object {
        //TODO Bookmarking on iOS
        suspend fun PlayerImpl.injectVideo(string: String) {
            if (string.startsWith("http://") || string.startsWith("https://") || string.startsWith("www") || string.startsWith("ftp://")) {
                injectVideoURL(string)
            } else {
                injectVideoFile(PlatformFile(string))
            }
        }
    }

    abstract suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote)
    abstract suspend fun injectVideoFileImpl(location: MediaFileLocation.Local)

    suspend fun injectVideoURL(url: String) = inject(url, { it.mediaFromUrl() }) { injectVideoURLImpl(it.location as MediaFileLocation.Remote) }
    suspend fun injectVideoFile(file: PlatformFile) = inject(file, { it.mediaFromFile() }) { injectVideoFileImpl(it.location as MediaFileLocation.Local) }

    private suspend inline fun <T> inject(source: T, crossinline toMedia: suspend (T) -> MediaFile, crossinline impl: suspend (MediaFile) -> Unit) {
        val media = toMedia(source)
        withContext(Dispatchers.Main) {
            try {
                delay(500) //Some players aren't ready right away (mpv, vlc)
                impl(media)
                parseMedia(media)
            } catch (e: Exception) {
                e.printStackTrace()
                viewmodel.osdManager.dispatchOSD {
                    getString(Res.string.room_msg_problem_loading_file)
                }
            }
        }
    }

    open suspend fun parseMedia(media: MediaFile) {
        videoEngineManager.media.value = media

        viewmodel.osdManager.dispatchOSD {
            getString(Res.string.room_selected_vid, "${viewmodel.media?.fileName}")
        }

        announceFileLoaded()

        changeSubtitleSize(SUBTITLE_SIZE.value())
    }


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
    abstract fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit)

    abstract fun getMaxVolume(): Int

    abstract fun getCurrentVolume(): Int

    abstract fun changeCurrentVolume(v: Int)

    fun announceFileLoaded() {
        if (viewmodel.isSoloMode) return

        viewmodel.networkManager.sendAsync<PacketOut.File> {
            media = viewmodel.media
        }
        viewmodel.networkManager.sendAsync<PacketOut.EmptyList>()
    }

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

    abstract val trackerJobInterval: Duration

    private val playerTrackerJob by lazy {
        playerScopeMain.launch {
            while (isActive) {
                if (isSeekable()) {
                    val pos = currentPositionMs()
                    videoEngineManager.timeCurrentMillis.value = pos
                    if (!viewmodel.isSoloMode) {
                        //TODO is this necessary ? viewmodel.protocolManager.globalPositionMs = pos.toDouble()
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


    open suspend fun reloadVideo() {}
}