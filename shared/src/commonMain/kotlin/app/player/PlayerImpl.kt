package app.player

import androidx.annotation.CallSuper
import androidx.annotation.UiThread
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFile.Companion.mediaFromFile
import app.player.models.MediaFile.Companion.mediaFromUrl
import app.player.models.MediaFileLocation
import app.player.models.Track
import app.preferences.Preferences.SUBTITLE_SIZE
import app.preferences.settings.SettingCategory
import app.preferences.value
import app.protocol.WireMessage
import app.room.toFileData
import app.room.RoomViewmodel
import app.utils.Platform
import app.utils.getFileName
import app.utils.platform
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
import kotlin.time.Duration.Companion.seconds

/** The actual platform-agnostic interface for video/audio playback in Syncplay.
 * Engines: ExoPlayer/MPV/VLC (Android), AVPlayer/VLC (iOS)*/
abstract class PlayerImpl(val viewmodel: RoomViewmodel, val engine: PlayerEngine) {

    val playerManager: PlayerManager = viewmodel.playerManager

    enum class TrackType {
        AUDIO, SUBTITLE
    }

    protected val playerSupervisorJob = SupervisorJob()
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
            viewmodel.dispatcher.sendSeek(newPosMs = chapter.timeOffsetMillis)
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
                    viewmodel.dispatcher.sendSeek(newPosMs = nextChapter.timeOffsetMillis)
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

                viewmodel.dispatchOSD {
                    getString(Res.string.room_selected_sub, filename)
                }
            } else {
                viewmodel.dispatchOSD {
                    getString(Res.string.room_selected_sub_error)
                }
            }
        } else {
            viewmodel.dispatchOSD {
                getString(Res.string.room_sub_error_load_vid_first)
            }
        }
    }

    abstract suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String)

    /** Loads a subtitle from a local file path (for downloaded subtitles). */
    suspend fun loadSubtitleFromPath(path: String, filename: String) {
        if (!isInitialized || !hasMedia()) return
        try {
            val extension = filename.substringAfterLast('.', "srt").lowercase()
            loadExternalSubImpl(PlatformFile(path), extension)
            viewmodel.dispatchOSD {
                getString(Res.string.room_selected_sub, filename)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            viewmodel.dispatchOSD {
                getString(Res.string.room_selected_sub_error)
            }
        }
    }

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
                viewmodel.dispatchOSD {
                    getString(Res.string.room_msg_problem_loading_file)
                }
            }
        }
    }

    open suspend fun parseMedia(media: MediaFile) {
        playerManager.media.value = media

        viewmodel.dispatchOSD {
            getString(Res.string.room_selected_vid, "${viewmodel.media?.fileName}")
        }

        if (platform == Platform.IOS) {
            //TODO Better come up with a better DSL to streamline file loading announcement
            announceFileLoaded()
        }

        changeSubtitleSize(SUBTITLE_SIZE.value())
    }

    abstract suspend fun pause()

    abstract suspend fun play()

    /** Sets playback speed (1.0 = normal, 0.95 = slowdown for sync). */
    abstract suspend fun setSpeed(speed: Double)

    abstract suspend fun isSeekable(): Boolean

    @UiThread
    @CallSuper
    open fun seekTo(toPositionMs: Long) {
        if (viewmodel.uiState.isInBackground) return
    }

    @UiThread
    abstract fun currentPositionMs(): Long

    abstract suspend fun switchAspectRatio(): String

    /** Takes a screenshot of the current video frame. Returns true if supported and successful. */
    open suspend fun takeScreenshot(): Boolean = false

    abstract suspend fun changeSubtitleSize(newSize: Int)

    @Composable
    abstract fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit)

    abstract fun getMaxVolume(): Int

    abstract fun getCurrentVolume(): Int

    abstract fun changeCurrentVolume(v: Int)

    fun announceFileLoaded() {
        if (viewmodel.isSoloMode) return

        viewmodel.media?.let { viewmodel.networkManager.sendAsync(WireMessage.file(it.toFileData())) }
        viewmodel.networkManager.sendAsync(WireMessage.listRequest())
    }

    fun onPlaybackEnded() {
        if (!isInitialized) return

        if (!viewmodel.isSoloMode) {
            if (viewmodel.session.sharedPlaylist.isEmpty()) return
            val currentIndex = viewmodel.session.spIndex.intValue
            val playlistSize = viewmodel.session.sharedPlaylist.size

            val next = if (playlistSize == currentIndex + 1) 0 else currentIndex + 1
            viewmodel.playlistManager.sendPlaylistSelection(next)
        }
    }

    abstract val trackerJobInterval: Duration

    val shouldTrackTimeManually: Boolean
        get() = trackerJobInterval != 0.seconds

    private val playerTrackerJob by lazy {
        playerScopeMain.launch {
            while (isActive) {
                if (isSeekable()) {
                    val pos = currentPositionMs()
                    playerManager.timeCurrentMillis.value = pos
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
        if (shouldTrackTimeManually) {
            playerTrackerJob
        }
    }


    open suspend fun reloadVideo() {}
}