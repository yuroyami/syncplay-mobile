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
import app.player.resolver.mediaResolver
import app.player.resolver.urlLooksLikeDirectMedia
import app.preferences.Preferences.MEDIA_RESOLVER_ENABLED
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
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource
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
import syncplaymobile.shared.generated.resources.room_msg_resolved_url
import syncplaymobile.shared.generated.resources.room_msg_resolving_url
import syncplaymobile.shared.generated.resources.room_selected_sub
import syncplaymobile.shared.generated.resources.room_selected_sub_error
import syncplaymobile.shared.generated.resources.room_selected_vid
import syncplaymobile.shared.generated.resources.room_sub_error_load_vid_first
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Below this media length, playlist auto-advance is suppressed — mirrors PC's
 *  `PLAYLIST_LOAD_NEXT_FILE_MINIMUM_LENGTH` (10s). Filters spurious end-of-file events from very
 *  short clips or failed loads. */
private const val PLAYLIST_ADVANCE_MIN_DURATION_MS = 10_000L

/** Auto-advance only when playback is within this window of the media's end, so a premature "ended"
 *  callback (e.g. from a load error) does not skip ahead. */
private const val PLAYLIST_ADVANCE_NEAR_END_MS = 5_000L

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

    /** Whether this engine can capture a still video frame via [takeScreenshot]. Gates the
     *  screenshot button in the room control panel so it only shows where it actually works. */
    open val supportsScreenshot: Boolean = false

    /** When true, the engine announces a freshly loaded file to the room itself, typically from
     *  a player "file-loaded" event once the real duration is known, so [parseMedia] must NOT
     *  also fire [announceFileLoaded] on iOS. Without this guard such engines announce the file
     *  twice (once prematurely with no duration, once correctly from the event). Engines that have
     *  no load event leave this false and rely on the [parseMedia] announce. */
    protected open val announcesFileLoadViaEvent: Boolean = false

    var isInitialized: Boolean = false

    /**
     * The local file whose iOS security scope we currently hold open for playback.
     *
     * A file resolved from a security-scoped bookmark must have its scope *active for as long as
     * the engine reads it* — not merely at the moment of injection. We start access when a file
     * is injected and release the previous one on the next injection (file or URL), so at most
     * one scope is ever held at a time. On Android the FileKit start/stop calls are no-ops.
     */
    private var scopedFile: PlatformFile? = null

    private fun beginScopedFileAccess(file: PlatformFile) {
        val previous = scopedFile
        if (previous != null && previous != file) runCatching { previous.stopAccessingSecurityScopedResource() }
        runCatching { file.startAccessingSecurityScopedResource() }
        scopedFile = file
    }

    private fun releaseScopedFileAccess() {
        scopedFile?.let { runCatching { it.stopAccessingSecurityScopedResource() } }
        scopedFile = null
    }

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
            viewmodel.dispatcher.pendingSeekFromMs = currentPos
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
                if (!viewmodel.isSoloMode) {
                    viewmodel.dispatcher.pendingSeekFromMs = currentMs
                    viewmodel.dispatcher.sendSeek(newPosMs = nextChapter.timeOffsetMillis)
                }
                viewmodel.player.seekTo(nextChapter.timeOffsetMillis)
            }
    }

    abstract suspend fun reapplyTrackChoices()

    suspend fun loadExternalSub(uri: PlatformFile) {
        if (!isInitialized) return

        if (hasMedia()) {
            val filename = getFileName(uri = uri).toString()
            val extension = filename.substringAfterLast('.', "srt").lowercase()

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


    abstract suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote)
    abstract suspend fun injectVideoFileImpl(location: MediaFileLocation.Local)

    /** Hands a URL to the player. If the URL is a "page URL" (YouTube, SoundCloud, …) and the
     *  platform resolver is enabled, the URL is first run through the resolver to extract a
     *  direct streamable URL plus best-effort title/duration metadata. Direct media URLs
     *  (`*.mp4`, `*.m3u8`, …) short-circuit the resolver entirely.
     *
     *  Resolution happens client-side at retrieve time — the shared playlist still stores the
     *  *original* page URL, since YouTube stream URLs are IP-pinned and time-limited and would
     *  not be valid across other clients in the room. Each client resolves independently from
     *  the same input. */
    suspend fun injectVideoURL(url: String) = inject(
        source = url,
        toMedia = { input ->
            val resolved = maybeResolve(input)
            val finalUrl = resolved?.directUrl ?: input
            finalUrl.mediaFromUrl().also { media ->
                resolved?.title?.takeIf { it.isNotBlank() }?.let { media.fileName = it }
                resolved?.durationSec?.let { media.fileDuration = it }
            }
        },
    ) {
        // Switching to a remote source: hand back any local file scope we were holding.
        releaseScopedFileAccess()
        injectVideoURLImpl(it.location as MediaFileLocation.Remote)
    }

    suspend fun injectVideoFile(file: PlatformFile) = inject(file, { it.mediaFromFile() }) {
        val location = it.location as MediaFileLocation.Local
        // Hold this file's security scope (iOS) open before the engine touches it, releasing
        // the previous file's scope. Without this, a bookmark-resolved URL is inaccessible the
        // instant FileKit's transient scope (from reading name/size) closes, and the engine
        // fails to open it.
        beginScopedFileAccess(location.file)
        injectVideoFileImpl(location)
    }

    private suspend fun maybeResolve(url: String) = when {
        !MEDIA_RESOLVER_ENABLED.value() -> null
        urlLooksLikeDirectMedia(url) -> null
        else -> {
            viewmodel.dispatchOSD { getString(Res.string.room_msg_resolving_url) }
            val resolved = mediaResolver.resolve(url)
            if (resolved != null) {
                viewmodel.dispatchOSD {
                    getString(Res.string.room_msg_resolved_url, resolved.title ?: resolved.directUrl)
                }
            }
            resolved
        }
    }

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

        if (platform == Platform.IOS && !announcesFileLoadViaEvent) {
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

        // Auto-advance applies online and in solo mode alike (PC's advanceToNextPlaylistItem has no
        // solo concept and always runs).
        val playlistSize = viewmodel.session.sharedPlaylist.size
        // PC only advances when there is more than one item; a single item would only repeat under a
        // looping option, which this app has none of, so a lone item just stops at its end.
        if (playlistSize <= 1) return

        val currentIndex = viewmodel.session.spIndex.intValue
        if (currentIndex !in 0 until playlistSize) return

        // Guard against spurious EOF (e.g. a load error firing "ended" near position 0): only advance
        // when the media is long enough (PC's PLAYLIST_LOAD_NEXT_FILE_MINIMUM_LENGTH = 10s) and we are
        // actually near the end. Otherwise a failed load would skip straight to the next item.
        val durationMs = playerManager.timeFullMillis.value
        val positionMs = currentPositionMs()
        if (durationMs <= PLAYLIST_ADVANCE_MIN_DURATION_MS) return
        if (durationMs - positionMs > PLAYLIST_ADVANCE_NEAR_END_MS) return

        // At the last item we stop rather than wrap to 0 — there is no "loop at end of playlist"
        // option, so looping would be wrong (PC returns here unless loopAtEndOfPlaylist is enabled).
        if (currentIndex + 1 >= playlistSize) return

        viewmodel.playlistManager.sendPlaylistSelection(currentIndex + 1)
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