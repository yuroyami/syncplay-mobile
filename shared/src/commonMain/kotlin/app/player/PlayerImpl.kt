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
import app.room.OSDCategory
import app.room.RoomViewmodel
import app.utils.Platform
import app.utils.ccExs
import app.utils.getFileName
import app.utils.loggy
import app.utils.platform
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_msg_problem_loading_file
import syncplaymobile.shared.generated.resources.room_msg_resolve_failed
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
 * Engines: ExoPlayer/mpv/KitePlayer (Android), AVPlayer/VLCKit/KitePlayer (iOS)*/
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

    /** Whether room drift correction may temporarily request a playback rate other than 1.0. */
    open val supportsSpeedAdjustment: Boolean = true

    /** When true, the engine announces a freshly loaded file to the room itself, typically from
     *  a player "file-loaded" event once the real duration is known, so [parseMedia] must NOT
     *  also fire [announceFileLoaded] on iOS. Without this guard such engines announce the file
     *  twice (once prematurely with no duration, once correctly from the event). Engines that have
     *  no load event leave this false and rely on the [parseMedia] announce. */
    protected open val announcesFileLoadViaEvent: Boolean = false

    /** Volatile: the destroy contract flips it on one thread while trackers and callbacks read it on others. */
    @Volatile
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
    private var scopedFileAccessStarted: Boolean = false

    /**
     * A media replacement is one transaction: resolve metadata, publish it, stop/open the engine,
     * and announce it. Serializing the whole sequence prevents two playlist events from crossing
     * their state, native handles, or file-descriptor ownership between those stages.
     */
    private val mediaInjectionMutex = Mutex()
    private val closing = atomic(false)

    private fun beginScopedFileAccess(file: PlatformFile) {
        // Re-loading the same file must not increment NSURL's grant count without a matching stop.
        if (scopedFile == file && scopedFileAccessStarted) return

        releaseScopedFileAccess()
        scopedFileAccessStarted = runCatching {
            file.startAccessingSecurityScopedResource()
        }.getOrDefault(false)
        scopedFile = file.takeIf { scopedFileAccessStarted }
    }

    /**
     * Releases the local file grant retained for playback.
     *
     * [PlayerManager] calls this after every engine teardown, including when [destroy] fails, so
     * an iOS room cannot strand a security-scoped resource until process exit.
     */
    private fun releaseScopedFileAccess() {
        if (scopedFileAccessStarted) {
            scopedFile?.let { runCatching { it.stopAccessingSecurityScopedResource() } }
        }
        scopedFile = null
        scopedFileAccessStarted = false
    }

    @UiThread
    abstract fun initialize()

    abstract suspend fun destroy()

    /**
     * Synchronously marks this instance closed, wakes engine-specific readiness waits, then waits
     * for any media replacement transaction before destroying its native state and file grant.
     */
    internal suspend fun destroyAndReleaseMedia() {
        if (!closing.compareAndSet(false, true)) return
        onClosing()
        mediaInjectionMutex.withLock {
            try {
                destroy()
            } finally {
                releaseScopedFileAccess()
            }
        }
    }

    /** Called before teardown waits on [mediaInjectionMutex], so an engine can wake load waiters. */
    protected open fun onClosing() = Unit

    abstract suspend fun configurableSettings(): SettingCategory?

    abstract suspend fun hasMedia(): Boolean

    abstract suspend fun isPlaying(): Boolean

    abstract suspend fun analyzeTracks(mediafile: MediaFile)

    abstract suspend fun selectTrack(track: Track?, type: TrackType)

    abstract suspend fun analyzeChapters(mediafile: MediaFile)

    @CallSuper
    open suspend fun jumpToChapter(chapter: Chapter) {
        if (!supportsChapters) return
        // The engine moves itself (by chapter index, not position), so only the announcement
        // and the undo record go through the dispatcher.
        viewmodel.dispatcher.announceSeek(chapter.timeOffsetMillis, fromMs = currentPositionMs())
    }

    fun skipChapter() {
        if (!supportsChapters) return

        val currentMs = currentPositionMs()

        viewmodel.media?.chapters
            ?.filter { it.timeOffsetMillis > currentMs }
            ?.minByOrNull { it.timeOffsetMillis }
            ?.let { nextChapter -> viewmodel.dispatcher.seek(nextChapter.timeOffsetMillis, fromMs = currentMs) }
    }

    abstract suspend fun reapplyTrackChoices()

    suspend fun loadExternalSub(uri: PlatformFile) {
        if (!isInitialized) return

        if (hasMedia()) {
            val filename = getFileName(uri = uri).toString()
            val extension = filename.substringAfterLast('.', "srt").lowercase()

            if (isValidSubtitleFile(extension)) {
                // An engine refusing the file must not throw out of the room's composition.
                val loaded = try {
                    loadExternalSubImpl(uri, extension)
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    loggy("External subtitle load failed: ${e.stackTraceToString()}")
                    false
                }
                viewmodel.dispatchOSD {
                    if (loaded) getString(Res.string.room_selected_sub, filename)
                    else getString(Res.string.room_selected_sub_error)
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

    /**
     * Loads a subtitle from a local file path (for downloaded subtitles). Returns true when the
     * engine accepted the file; user-facing messaging is the caller's responsibility, so the
     * subtitle-search UI can drive its progress/checkmark/error states off the result.
     */
    suspend fun loadSubtitleFromPath(path: String, filename: String): Boolean {
        if (!isInitialized || !hasMedia()) return false
        return try {
            val extension = filename.substringAfterLast('.', "srt").lowercase()
            loadExternalSubImpl(PlatformFile(path), extension)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            loggy("Downloaded subtitle load failed: ${e.stackTraceToString()}")
            false
        }
    }

    /** The picker offers [ccExs]; an engine that cannot parse one of them reports its own error. */
    private fun isValidSubtitleFile(extension: String) = extension.lowercase() in ccExs


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
            } else {
                // Said out loud: the raw page URL is handed to the engine next, and its failure
                // would otherwise be the first sign that nothing was resolved.
                viewmodel.dispatchOSD(OSDCategory.WARNING) { getString(Res.string.room_msg_resolve_failed) }
            }
            resolved
        }
    }

    /**
     * Optional settling delay (ms) applied before a load command is issued in [inject]. Defaults
     * to 0: mobile's in-process engines are constructed synchronously in [initialize] and self-guard
     * on `isInitialized`, so there is nothing to wait for. An engine that genuinely needs a post-init
     * settle window may override this to a positive value.
     */
    protected open val injectSettleDelayMs: Long = 0L

    private suspend inline fun <T> inject(
        source: T,
        crossinline toMedia: suspend (T) -> MediaFile,
        crossinline impl: suspend (MediaFile) -> Unit,
    ) {
        mediaInjectionMutex.withLock {
            if (closing.value) throw CancellationException("Player is closing")
            val media = toMedia(source)
            withContext(Dispatchers.Main) {
                try {
                    if (injectSettleDelayMs > 0) delay(injectSettleDelayMs)
                    // Install the new media BEFORE the engine load command: engine load events can
                    // fire DURING impl() (mpv's START_FILE, VLCKit's LengthChanged) and they read
                    // viewmodel.media for the room announce. With the old install-after-impl order,
                    // a 2nd+ injected file could get announced with the PREVIOUS file's name, size
                    // and duration.
                    installMedia(media)
                    impl(media)
                    parseMedia(media)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    e.printStackTrace()
                    viewmodel.dispatchOSD {
                        getString(Res.string.room_msg_problem_loading_file)
                    }
                }
            }
        }
    }

    /**
     * Installs [media] as the active file, synchronously and before the engine sees the load
     * command. Split out of [parseMedia] so no engine callback can ever observe the previous
     * file's state after a new injection started.
     */
    private fun installMedia(media: MediaFile) {
        // Arm position masking BEFORE attaching the file. The engine sits at ~0 until the first-sync
        // seek lands; advertising that 0 would make the server adopt us as the slowest watcher and
        // rewind everyone (see awaitingRoomResyncDeadline). Arming first guarantees an inbound-State
        // ACK can never observe media!=null with the mask still disarmed; while media is still null
        // the reporter already falls back to the room position, so the ordering is safe.
        if (!viewmodel.isSoloMode) viewmodel.protocol.markAwaitingRoomResync()
        playerManager.media.value = media
        // Arm the room re-anchor for this fresh file (see [fileLoadResyncPending] /
        // ProtocolManager.reanchorSyncOnFileLoad).
        fileLoadResyncPending = true
        // Wipe the previous file's duration: engines (and mpv's duration-wait loop) treat a
        // positive value as "this file's duration is known". Leaking the old value made the
        // very first announce of a newly injected file carry stale metadata. The playhead goes
        // too, or the previous file's position feeds the desync comparison until the first tick.
        playerManager.timeFullMillis.value = 0L
        playerManager.samplePosition(0L)
        playerManager.timeBufferedMillis.value = -1L
    }

    /**
     * Set by [installMedia] the instant a NEW media is installed, consumed once by
     * [announceFileLoaded] to re-anchor room sync for that file. Armed synchronously together
     * with `media.value`, before the engine load command even runs, so an early engine
     * load callback (e.g. VLCKit's `mediaPlayerLengthChanged`) cannot fire
     * [announceFileLoaded] before the flag exists. Guarantees the re-anchor happens exactly
     * once per load even though [announceFileLoaded] itself may fire several times
     * (HLS/DASH length refinements).
     */
    private var fileLoadResyncPending = false

    open suspend fun parseMedia(media: MediaFile) {
        // Media installation (mask arming, media.value, resync flag, duration wipe) already
        // happened in [installMedia] before the engine load command; this stage only handles
        // the user-visible OSD, the iOS no-event announce path, and subtitle sizing.
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

    /**
     * Every engine calls this first. The tracker cache takes the target at once, so the very next
     * State ACK advertises where the engine is heading rather than a sample from before the seek,
     * which the server would otherwise adopt as the room's slowest position.
     */
    @UiThread
    @CallSuper
    open fun seekTo(toPositionMs: Long) {
        playerManager.samplePosition(toPositionMs.coerceAtLeast(0L))
    }

    @UiThread
    abstract fun currentPositionMs(): Long

    /** How far playback is buffered, or null when the engine cannot say. The seekbar draws no band for null. */
    @UiThread
    open fun bufferedPositionMs(): Long? = null

    abstract suspend fun switchAspectRatio(): String

    abstract suspend fun changeSubtitleSize(newSize: Int)

    @Composable
    abstract fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit)

    /** The engine's own output, 0 to 100. Where the platform owns the base this stays at 100. */
    abstract fun getEngineVolume(): Int
    abstract fun setEngineVolume(percent: Int)

    /** The engine's gain ceiling in percent; 100 means it cannot amplify. */
    open val gainMax: Int = VolumeLadder.BASE_MAX

    /** Amplification, 100 to [gainMax]. Only consulted when [gainMax] is above 100. */
    open fun getGain(): Int = VolumeLadder.BASE_MAX
    open fun setGain(percent: Int) {}

    /** The one ladder the room moves: base first, then gain where the engine has any. */
    val volume: VolumeController by lazy { VolumeController(this) }

    fun announceFileLoaded() {
        if (viewmodel.isSoloMode) return

        viewmodel.media?.let { viewmodel.networkManager.sendAsync(WireMessage.file(it.toFileData())) }
        viewmodel.networkManager.sendAsync(WireMessage.listRequest())
        // The loader is warned about a mismatch too, not only everyone else in the room.
        viewmodel.checkFileMismatches()

        // Re-anchor room sync once per loaded file, now that the engine reports the file as
        // loaded (and is therefore seekable before the next State arrives). Without this a file
        // that finished loading AFTER the first server State never adopts the room's position/
        // play-state. media != null is implied by reaching here after a load, but guard anyway —
        // an engine callback could call this before media.value is set on some path.
        if (fileLoadResyncPending && viewmodel.media != null) {
            fileLoadResyncPending = false
            viewmodel.protocol.reanchorSyncOnFileLoad()
        }
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
                    playerManager.samplePosition(currentPositionMs())
                    playerManager.timeBufferedMillis.value = bufferedPositionMs() ?: -1L
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
