package com.yuroyami.syncplay.managers.player

import androidx.annotation.CallSuper
import androidx.annotation.UiThread
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuroyami.syncplay.managers.protocol.creator.PacketOut
import com.yuroyami.syncplay.managers.settings.SettingCategory
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.MediaFile.Companion.mediaFromFile
import com.yuroyami.syncplay.models.MediaFile.Companion.mediaFromUrl
import com.yuroyami.syncplay.models.MediaFileLocation
import com.yuroyami.syncplay.models.Track
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

/**
 * Abstract base class for media player implementations in Syncplay.
 *
 * Provides a platform-agnostic interface for video/audio playback with features like:
 * - Media loading from local files or URLs
 * - Playback control (play, pause, seek)
 * - Track management (audio, subtitles)
 * - Chapter navigation
 * - Volume control
 * - Playback progress tracking
 *
 * ## Available Player Engines
 *
 * **Android:**
 * - ExoPlayer (Google) - Most stable, limited format support
 * - MPV - Most powerful, supports most formats, mildly stable
 * - VLC - Widest format support (QuickTime, Xvid), powerful but unstable
 *
 * **iOS:**
 * - AVPlayer - Apple's native media player, access to it is available via Kotlin ObjC interop
 * - VLC  - Default player to use, powerful and versatile, provided via MobileVLCKit pod
 *
 * Concrete implementations delegate to platform-specific player frameworks while
 * maintaining a consistent API for Syncplay's synchronization logic.
 *
 * @property viewmodel The parent RoomViewModel managing this player
 * @property engine The player engine type being used
 */
abstract class BasePlayer(
    val viewmodel: RoomViewmodel,
    val engine: PlayerEngine
) {
    /**
     * Reference to the player manager that owns this player instance.
     */
    val playerManager: PlayerManager = viewmodel.playerManager

    /**
     * Types of media tracks that can be selected.
     */
    enum class TRACKTYPE {
        /** Audio track (language, codec) */
        AUDIO,
        /** Subtitle/closed caption track */
        SUBTITLE
    }

    /**
     * Supervisor job for player-related coroutines.
     * Ensures child job failures don't cancel the entire player scope.
     */
    private val playerSupervisorJob = SupervisorJob()

    /**
     * Coroutine scope for main thread operations (UI updates, player commands).
     */
    val playerScopeMain = CoroutineScope(Dispatchers.Main + playerSupervisorJob)

    /**
     * Coroutine scope for IO operations (file loading, network requests).
     */
    val playerScopeIO = CoroutineScope(Dispatchers.IO + playerSupervisorJob)

    /**
     * Whether this player supports changing aspect ratio dynamically.
     */
    abstract val canChangeAspectRatio: Boolean

    /**
     * Whether this player supports chapter navigation.
     */
    abstract val supportsChapters: Boolean

    /**
     * Whether the player has been initialized and is ready for use.
     */
    var isInitialized: Boolean = false

    /**
     * Initializes the player and prepares it for media playback.
     * Must be called on the UI thread.
     */
    @UiThread
    abstract fun initialize()

    /**
     * Destroys the player and releases all resources.
     * Called when leaving the room or switching players.
     */
    abstract suspend fun destroy()

    /**
     * Returns platform-specific configuration settings for this player, if any.
     *
     * @return Bundle of extra settings, or null if no custom settings available
     */
    abstract suspend fun configurableSettings(): SettingCategory?

    /**
     * Checks whether any media is currently loaded in the player.
     *
     * @return true if media is loaded, false otherwise
     */
    abstract suspend fun hasMedia(): Boolean

    /**
     * Checks whether playback is currently active (not paused).
     *
     * @return true if playing, false if paused or stopped
     */
    abstract suspend fun isPlaying(): Boolean

    /**
     * Analyzes and extracts available tracks from the loaded media.
     *
     * Populates the media file's track information (audio, subtitles).
     *
     * @param mediafile The media file to analyze
     */
    abstract suspend fun analyzeTracks(mediafile: MediaFile)

    /**
     * Selects a specific track for playback.
     *
     * @param track The track to select, or null to disable that track type
     * @param type Whether this is an audio or subtitle track
     */
    abstract suspend fun selectTrack(track: Track?, type: TRACKTYPE)

    /**
     * Analyzes and extracts chapter information from the loaded media.
     *
     * @param mediafile The media file to analyze for chapters
     */
    abstract suspend fun analyzeChapters(mediafile: MediaFile)

    /**
     * Jumps to a specific chapter in the media.
     *
     * @param chapter The chapter to jump to
     */
    abstract suspend fun jumpToChapter(chapter: Chapter)

    /**
     * Skips to the next chapter in the media.
     */
    abstract suspend fun skipChapter()

    /**
     * Reapplies previously selected track choices after media change.
     * Useful for maintaining user preferences across playlist items.
     */
    abstract suspend fun reapplyTrackChoices()

    /**
     * Loads an external subtitle file from a URI.
     *
     * Validates the file extension, loads the subtitle if valid, and displays
     * appropriate feedback messages to the user.
     *
     * @param uri The URI of the subtitle file to load
     */
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

    /**
     * Platform-specific implementation for loading external subtitle files.
     *
     * @param uri The URI of the subtitle file
     * @param extension The file extension (e.g., "srt", "ass")
     */
    abstract suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String)

    /**
     * Validates whether a file extension represents a supported subtitle format.
     *
     * @param extension The file extension to check
     * @return true if the extension is a valid subtitle format
     */
    private fun isValidSubtitleFile(extension: String) =
        listOf("srt", "ass", "ssa", "ttml", "vtt").any { it in extension.lowercase() }


    companion object {
        //TODO Bookmarking on iOS
        suspend fun BasePlayer.injectVideo(string: String) {
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

    open fun parseMedia(media: MediaFile) {
        playerManager.media.value = media

        /* Finally, show a a toast to the user that the media file has been added */
        viewmodel.osdManager.dispatchOSD {
            getString(Res.string.room_selected_vid, "${viewmodel.media?.fileName}")
        }

        announceFileLoaded()
    }


    /**
     * Pauses playback.
     */
    abstract suspend fun pause()

    /**
     * Resumes or starts playback.
     */
    abstract suspend fun play()

    /**
     * Checks whether the current media supports seeking.
     *
     * @return true if seeking is supported, false for live streams or unsupported formats
     */
    abstract suspend fun isSeekable(): Boolean

    /**
     * Seeks to a specific position in the media.
     *
     * Base implementation checks if the app is in background to prevent seeks
     * during lifecycle transitions. Subclasses should call super and add their seek logic.
     *
     * @param toPositionMs The target position in milliseconds
     */
    @UiThread
    @CallSuper
    open fun seekTo(toPositionMs: Long) {
        if (viewmodel.lifecycleManager.isInBackground) return
    }

    /**
     * Gets the current playback position.
     * Must be called on the UI thread.
     *
     * @return Current position in milliseconds
     */
    @UiThread
    abstract fun currentPositionMs(): Long

    /**
     * Cycles to the next aspect ratio mode and returns its name.
     *
     * @return The name of the newly selected aspect ratio
     */
    abstract suspend fun switchAspectRatio(): String

    /**
     * Changes the subtitle font size.
     *
     * @param newSize The new subtitle size (platform-specific units)
     */
    abstract suspend fun changeSubtitleSize(newSize: Int)

    /**
     * Composable function that renders the video player surface.
     *
     * @param modifier Compose modifier for styling and layout
     */
    @Composable
    abstract fun VideoPlayer(modifier: Modifier)

    /**
     * Gets the maximum volume level for this player.
     *
     * @return Maximum volume value
     */
    abstract fun getMaxVolume(): Int

    /**
     * Gets the current volume level.
     *
     * @return Current volume value
     */
    abstract fun getCurrentVolume(): Int

    /**
     * Sets the player volume.
     *
     * @param v The new volume level (0 to max)
     */
    abstract fun changeCurrentVolume(v: Int)

    /**
     * Sends the current media file information to the Syncplay server.
     *
     * Notifies other users in the room about the loaded file.
     */
    fun announceFileLoaded() {
        viewmodel.networkManager.sendAsync<PacketOut.File> {
            media = viewmodel.media
        }
        viewmodel.networkManager.sendAsync<PacketOut.EmptyList>()
    }

    /**
     * Called when media playback reaches the end.
     *
     * In online mode with shared playlist, automatically advances to the next
     * playlist item (or loops to the first item).
     */
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

    /**
     * The interval at which to update playback position tracking.
     * Platform-specific based on performance characteristics.
     */
    abstract val trackerJobInterval: Duration

    /**
     * Coroutine job that continuously tracks playback progress.
     *
     * Updates the current position state at regular intervals for UI display
     * and synchronization purposes. Lazily initialized on first access.
     */
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

    /**
     * Starts tracking playback progress.
     *
     * Accessing the lazy playerTrackerJob property initiates the tracking coroutine
     * if it hasn't been started yet.
     */
    fun startTrackingProgress() {
        // Accessing playerTrackerJob here will start it if it hasn't started yet
        playerTrackerJob
    }


    open suspend fun reloadVideo() {}
}