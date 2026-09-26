package app.player

import androidx.annotation.CallSuper
import androidx.annotation.UiThread
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.i18n.Localization
import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFile.Companion.mediaFromFile
import app.player.models.MediaFile.Companion.mediaFromUrl
import app.player.models.MediaFileLocation
import app.player.models.PlayerOptions
import app.player.models.Track
import app.player.models.TrackChoice
import app.player.models.VisualizerControls
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
import app.utils.LogRedactor
import app.utils.Platform
import app.utils.ccExs
import app.utils.getFileName
import app.utils.ioDispatcher
import app.utils.loggy
import app.utils.platform
import app.utils.platformFileAt
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** At or below this media length, the playlist does not auto-advance. The value mirrors
 *  `PLAYLIST_LOAD_NEXT_FILE_MINIMUM_LENGTH` (10 s) of the Syncplay PC client. It filters out false
 *  end-of-file events from very short clips or failed loads. */
private const val PLAYLIST_ADVANCE_MIN_DURATION_MS = 10_000L

/** Auto-advance happens only within this distance of the media's end, so an early "ended"
 *  callback (for example from a load error) does not skip ahead. */
private const val PLAYLIST_ADVANCE_NEAR_END_MS = 5_000L

/** The position tracker's interval while the engine neither plays nor buffers. A stopped playhead
 *  needs only a slow check, to notice a position that the seek path did not record itself. */
private val IDLE_TRACKER_INTERVAL = 1.seconds

/** The platform-independent player that a room (the group of people watching together) drives.
 *  Each engine has its own subclass: ExoPlayer, mpv and KitePlayer on Android; AVPlayer, VLCKit
 *  and KitePlayer on iOS; KitePlayer on desktop; the browser's video element on the web. */
abstract class PlayerImpl(val viewmodel: RoomViewmodel, val engine: PlayerEngine) {

    val playerManager: PlayerManager = viewmodel.playerManager

    enum class TrackType {
        AUDIO, SUBTITLE, VIDEO
    }

    protected val playerSupervisorJob = SupervisorJob()
    val playerScopeMain = CoroutineScope(Dispatchers.Main + playerSupervisorJob)
    val playerScopeIO = CoroutineScope(ioDispatcher + playerSupervisorJob)

    open val canChangeAspectRatio: Boolean = true
    abstract val supportsChapters: Boolean
    open val supportsPictureInPicture: Boolean = true
    open val supportsVideoTrackSelection: Boolean = false
    open val supportsAudioVisualization: Boolean = false

    /** The audio visualizer's controls while it draws, null otherwise. Only KitePlayer has them. */
    open val visualizer: StateFlow<VisualizerControls?> = MutableStateFlow(null)

    /** Whether room drift correction may temporarily request a playback rate other than 1.0. */
    open val supportsSpeedAdjustment: Boolean = true

    /** True when the engine reports a new file itself, from its own "file loaded" event, through
     *  [onEngineFileReady]. [parseMedia] then skips its iOS call. An engine with no load event
     *  leaves this false and relies on the [parseMedia] call. */
    protected open val announcesFileLoadViaEvent: Boolean = false

    /** Volatile: teardown sets it on one thread while trackers and callbacks read it on others. */
    @Volatile
    var isInitialized: Boolean = false

    /**
     * The local file whose iOS security scope is open for playback.
     *
     * A file resolved from a security-scoped bookmark needs its scope open for as long as the
     * engine reads the file, not only at the moment of injection. Access starts when a file is
     * injected, and the next injection (file or URL) releases it. So at most one media file scope
     * is open at a time. On Android and the web, the FileKit start and stop calls do nothing.
     */
    private var scopedFile: PlatformFile? = null
    private var scopedFileAccessStarted: Boolean = false

    /**
     * External subtitle files whose iOS scope stays open together with the media's scope.
     *
     * The user picks a subtitle separately from the video, so the media's grant does not cover
     * it. Engines also read a subtitle lazily, not at load time. Without an open scope, the engine
     * is refused access to a subtitle that the user picked on iOS. One video can have several
     * subtitles, so this list holds each grant once and releases them all with the media grant.
     */
    private val scopedSubtitleFiles = mutableListOf<PlatformFile>()

    /**
     * Makes a media replacement one transaction: resolve the metadata, publish it, open the file
     * in the engine and announce it. Running the whole sequence under one lock keeps two playlist
     * events from mixing their state, native handles or file descriptors between the steps.
     */
    private val mediaInjectionMutex = Mutex()
    private val closing = atomic(false)

    private fun beginScopedFileAccess(file: PlatformFile) {
        // Loading the same file again must not add to NSURL's grant count without a matching stop.
        if (scopedFile == file && scopedFileAccessStarted) return

        releaseScopedFileAccess()
        scopedFileAccessStarted = runCatching {
            file.startAccessingSecurityScopedResource()
        }.getOrDefault(false)
        scopedFile = file.takeIf { scopedFileAccessStarted }
    }

    /**
     * Releases the local file grant held for playback, and the subtitle grants with it.
     *
     * [destroyAndReleaseMedia] calls this after every engine teardown, even when [destroy] throws,
     * so an iOS room cannot keep a security-scoped resource open until the process exits.
     */
    private fun releaseScopedFileAccess() {
        if (scopedFileAccessStarted) {
            scopedFile?.let { runCatching { it.stopAccessingSecurityScopedResource() } }
        }
        scopedFile = null
        scopedFileAccessStarted = false
        releaseScopedSubtitleAccess()
    }

    /** Takes the grant for a picked subtitle and keeps it for as long as the engine may read it. */
    private fun beginScopedSubtitleAccess(file: PlatformFile) {
        if (scopedSubtitleFiles.any { it == file }) return
        val started = runCatching { file.startAccessingSecurityScopedResource() }.getOrDefault(false)
        if (started) scopedSubtitleFiles += file
    }

    private fun releaseScopedSubtitleAccess() {
        scopedSubtitleFiles.forEach { runCatching { it.stopAccessingSecurityScopedResource() } }
        scopedSubtitleFiles.clear()
    }

    @UiThread
    abstract fun initialize()

    abstract suspend fun destroy()

    /**
     * Marks this instance closed at once and wakes the engine's load waits. Then it waits for any
     * media replacement to finish before it destroys the native state and releases the file grant.
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

    /** True once teardown has started. An engine that waits on a load should give up then. */
    protected val isClosing: Boolean get() = closing.value

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

    /**
     * True when the engine applies the preferred audio and subtitle languages by itself, as
     * ExoPlayer's track selector and mpv's core options do. [applyTrackRules] then only restores
     * the picks carried over from the previous file.
     */
    protected open val appliesPreferredLanguagesItself: Boolean = false

    /**
     * Applies the picks carried over from the previous file, then the preferred languages, to a
     * newly read track list. It runs once per file, from [onEngineFileReady]. Opening a panel
     * only reads the tracks and changes nothing.
     *
     * A track type that the user already picked in this file is left alone. A track with no
     * language tag never matches, so an engine without tags keeps its own native handling.
     */
    private suspend fun applyTrackRules(mediafile: MediaFile) {
        val options = PlayerOptions.get()
        val preferred = mapOf(
            TrackType.AUDIO to options.audioPreference,
            TrackType.SUBTITLE to options.ccPreference,
        )
        for ((type, language) in preferred) {
            when (val choice = playerManager.currentTrackChoices[type]) {
                null -> Unit
                TrackChoice.Off -> {
                    selectTrack(null, type)
                    continue
                }
                is TrackChoice.ByLanguage -> {
                    val same = mediafile.tracks.firstOrNull { it.type == type && it.language.equals(choice.language, ignoreCase = true) }
                    if (same != null) {
                        selectTrack(same, type)
                        continue
                    }
                }
                // A pick in this file.
                else -> continue
            }
            if (appliesPreferredLanguagesItself || language == "und" || language.isBlank()) continue
            mediafile.tracks
                .firstOrNull { it.type == type && it.language?.contains(language, ignoreCase = true) == true }
                ?.let { selectTrack(it, type) }
        }
    }

    /**
     * Restores index-addressed track picks (mpv, VLCKit, AVPlayer) by handing them back to
     * [selectTrack]. An engine that stores an opaque override, as ExoPlayer does, restores it
     * itself instead.
     */
    protected suspend fun reapplyIndexedTrackChoices() {
        val tracks = playerManager.media.value?.tracks ?: return
        for (type in TrackType.entries) {
            when (val choice = playerManager.currentTrackChoices[type]) {
                null -> {}
                TrackChoice.Off -> selectTrack(null, type)
                is TrackChoice.ByIndex -> tracks
                    .firstOrNull { it.type == type && it.index == choice.index }
                    ?.let { selectTrack(it, type) }
                is TrackChoice.ByOverride, is TrackChoice.ByLanguage -> {}
            }
        }
    }

    suspend fun loadExternalSub(uri: PlatformFile) {
        if (!isInitialized) return

        if (hasMedia()) {
            val filename = getFileName(uri = uri).toString()
            val extension = filename.substringAfterLast('.', "srt").lowercase()

            if (isValidSubtitleFile(extension)) {
                // An engine that refuses the file must not throw out of the room's composition.
                val loaded = try {
                    beginScopedSubtitleAccess(uri)
                    loadExternalSubImpl(uri, extension)
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    loggy("External subtitle load failed: ${e.stackTraceToString()}")
                    false
                }
                if (loaded) viewmodel.dispatchOSD { Localization.strings.roomSelectedSub(filename) }
                else viewmodel.dispatchWarning { Localization.strings.roomSelectedSubError }
            } else {
                viewmodel.dispatchWarning { Localization.strings.roomSelectedSubError }
            }
        } else {
            viewmodel.dispatchWarning { Localization.strings.roomSubErrorLoadVidFirst }
        }
    }

    abstract suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String)

    /**
     * Loads a subtitle from a local file path (for downloaded subtitles). Returns true when the
     * engine accepted the file. The caller shows any message, so the subtitle search UI can set
     * its progress, checkmark and error states from the result.
     */
    suspend fun loadSubtitleFromPath(path: String, filename: String): Boolean {
        if (!isInitialized || !hasMedia()) return false
        return try {
            val extension = filename.substringAfterLast('.', "srt").lowercase()
            loadExternalSubImpl(platformFileAt(path), extension)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            loggy("Downloaded subtitle load failed: ${e.stackTraceToString()}")
            false
        }
    }

    /** The picker offers [ccExs]. An engine that cannot parse one of them reports its own error. */
    private fun isValidSubtitleFile(extension: String) = extension.lowercase() in ccExs


    abstract suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote)
    abstract suspend fun injectVideoFileImpl(location: MediaFileLocation.Local)

    /** Hands a URL to the player. When the URL is a page URL (YouTube, SoundCloud and so on) and
     *  the media resolver is on, the resolver first extracts a direct stream URL, plus the title
     *  and duration when it can. A direct media URL (`*.mp4`, `*.m3u8` and so on) skips the
     *  resolver.
     *
     *  Each client resolves the URL itself, at load time. The shared playlist (the file list that
     *  everyone in the room follows) keeps the original page URL. YouTube stream URLs are tied to
     *  one IP address and expire, so they do not work for the other clients in the room. */
    suspend fun injectVideoURL(url: String, onInstalled: (MediaFile) -> Unit = {}) = inject(
        source = url,
        onInstalled = onInstalled,
        toMedia = { input ->
            val resolved = maybeResolve(input)
            val finalUrl = resolved?.directUrl ?: input
            finalUrl.mediaFromUrl().also { media ->
                resolved?.title?.takeIf { it.isNotBlank() }?.let { media.fileName = it }
                resolved?.durationSec?.let { media.fileDuration = it }
            }
        },
    ) {
        // A remote source needs no local file, so release any local file scope.
        releaseScopedFileAccess()
        injectVideoURLImpl(it.location as MediaFileLocation.Remote)
    }

    suspend fun injectVideoFile(file: PlatformFile, onInstalled: (MediaFile) -> Unit = {}) = inject(file, onInstalled, { it.mediaFromFile() }) {
        val location = it.location as MediaFileLocation.Local
        // Open this file's security scope (iOS) before the engine touches the file, and release
        // the previous file's scope. Without an open scope, a bookmark-resolved URL becomes
        // inaccessible as soon as FileKit's short scope (for reading the name and size) closes,
        // and the engine fails to open it.
        beginScopedFileAccess(location.file)
        injectVideoFileImpl(location)
    }

    private suspend fun maybeResolve(url: String) = when {
        !MEDIA_RESOLVER_ENABLED.value() -> null
        urlLooksLikeDirectMedia(url) -> null
        else -> {
            viewmodel.dispatchOSD { Localization.strings.roomMsgResolvingUrl }
            val resolved = mediaResolver.resolve(url)
            if (resolved != null) {
                viewmodel.dispatchOSD {
                    Localization.strings.roomMsgResolvedUrl(resolved.title ?: resolved.directUrl)
                }
            } else {
                // Show a warning. The raw page URL goes to the engine next, and without the warning
                // the engine's failure would be the first sign that nothing was resolved.
                viewmodel.dispatchOSD(OSDCategory.WARNING) { Localization.strings.roomMsgResolveFailed }
            }
            resolved
        }
    }

    /**
     * An optional delay (ms) before [inject] sends the load command. The default is 0: the
     * in-process engines are built synchronously in [initialize] and check `isInitialized`
     * themselves, so there is nothing to wait for. An engine that needs time to settle after
     * [initialize] can override this with a positive value.
     */
    protected open val injectSettleDelayMs: Long = 0L

    /**
     * Loads one file: [toMedia] builds it, [installMedia] makes it the active file, [onInstalled]
     * hands it to the caller before any engine event can arrive, and [impl] gives it to the engine.
     */
    private suspend inline fun <T> inject(
        source: T,
        noinline onInstalled: (MediaFile) -> Unit,
        crossinline toMedia: suspend (T) -> MediaFile,
        crossinline impl: suspend (MediaFile) -> Unit,
    ) {
        mediaInjectionMutex.withLock {
            if (closing.value) throw CancellationException("Player is closing")
            val media = toMedia(source)
            withContext(Dispatchers.Main) {
                try {
                    if (injectSettleDelayMs > 0) delay(injectSettleDelayMs)
                    // Install the new media before the engine load command. Engine load events can
                    // fire during impl() (mpv's START_FILE, VLCKit's LengthChanged), and they read
                    // viewmodel.media for the room announce. If the install came after impl(), a
                    // second or later file could be announced with the previous file's name, size
                    // and duration.
                    installMedia(media)
                    onInstalled(media)
                    impl(media)
                    parseMedia(media)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    e.printStackTrace()
                    viewmodel.dispatchWarning { Localization.strings.roomMsgProblemLoadingFile }
                    onEngineLoadFailed(media)
                }
            }
        }
    }

    /**
     * Installs [media] as the active file, synchronously and before the engine sees the load
     * command. It runs apart from [parseMedia], so that no engine callback can see the previous
     * file's state after a new injection starts.
     */
    private fun installMedia(media: MediaFile) {
        // Turn on position masking (the client reports the room's position, not its own) before
        // attaching the file. The engine stays near 0 until the first sync seek lands. Reporting
        // that 0 would make the server treat this client as the slowest watcher and rewind
        // everyone (see awaitingRoomResyncDeadline). Masking first means that the acknowledgement
        // of an incoming State never sees media != null with the mask off. While media is null,
        // the reporter already falls back to the room position, so this order is safe.
        if (!viewmodel.isSoloMode) viewmodel.protocol.markAwaitingRoomResync()
        LogRedactor.register(LogRedactor.Kind.File, media.fileName)
        // An open offer to continue belongs to the previous file.
        viewmodel.resume.onMediaReplaced()
        // A track index from the previous file means nothing in this one. Only a language carries over.
        playerManager.currentTrackChoices = playerManager.currentTrackChoices.forNextFile()
        playerManager.media.value = media
        // Arm the room re-anchor for this new file (see [fileLoadResyncPending] and
        // ProtocolManager.reanchorSyncOnFileLoad).
        fileLoadResyncPending = true
        // Clear the previous file's duration. Engines (and mpv's duration wait loop) treat a
        // positive value as "this file's duration is known", so a leftover value would make the
        // first announce of the new file carry stale metadata. Reset the playhead too, or the
        // previous file's position feeds the desync check until the first tracker tick.
        playerManager.timeFullMillis.value = 0L
        playerManager.samplePosition(0L)
        playerManager.timeBufferedMillis.value = -1L
    }

    /**
     * True from the moment [installMedia] installs a new media until [announceFileLoaded] uses it
     * once to re-anchor room sync (the next server State then sets this file's position and play
     * state again). [installMedia] sets it together with `media.value`, before the engine load
     * command runs, so an early engine load callback (for example VLCKit's
     * `mediaPlayerLengthChanged`) cannot call [announceFileLoaded] before the flag is set. The
     * re-anchor happens exactly once per load, even though [announceFileLoaded] can run several
     * times (HLS and DASH streams refine the length).
     */
    private var fileLoadResyncPending = false

    open suspend fun parseMedia(media: MediaFile) {
        // [installMedia] already did the setup (mask, media.value, resync flag, duration reset)
        // before the engine load command. This step only shows the OSD (on-screen) message, runs
        // the iOS announce for engines without a load event, and sets the subtitle size.
        viewmodel.dispatchOSD {
            Localization.strings.roomSelectedVid("${viewmodel.media?.fileName}")
        }

        if (platform == Platform.IOS && !announcesFileLoadViaEvent) {
            onEngineFileReady(playerManager.timeFullMillis.value)
        }

        changeSubtitleSize(SUBTITLE_SIZE.value())
    }

    abstract suspend fun pause()

    abstract suspend fun play()

    /** Sets playback speed (1.0 = normal, 0.95 = slowdown for sync). */
    abstract suspend fun setSpeed(speed: Double)

    abstract suspend fun isSeekable(): Boolean

    /**
     * Checks a user seek before it is announced to the room. An engine may adjust the target or
     * reject it with null, but must not move playback here. A deferred startup seek can still be
     * clamped again once the native duration is known.
     */
    @UiThread
    open suspend fun prepareSeekTarget(targetMs: Long): Long? = targetMs

    /**
     * Every engine override calls this before it moves playback. The tracker cache takes the
     * target at once, so the next State acknowledgement reports where the engine is going, not a
     * sample from before the seek. The server would otherwise adopt that old sample as the room's
     * slowest position.
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

    /** The engine's own output, 0 to 100. Where the platform owns the base, this stays at 100. */
    abstract fun getEngineVolume(): Int
    abstract fun setEngineVolume(percent: Int)

    /** The engine's gain ceiling in percent; 100 means it cannot amplify. */
    open val gainMax: Int = VolumeLadder.BASE_MAX

    /** Amplification, 100 to [gainMax]. Only read when [gainMax] is above 100. */
    open fun getGain(): Int = VolumeLadder.BASE_MAX
    open fun setGain(percent: Int) {}

    /** The room's volume control: the base first, then the gain where the engine has any. */
    val volume: VolumeController by lazy { VolumeController(this) }

    /** The media that [onEngineFileReady] announced. Each new media is announced once. */
    @Volatile
    private var announcedMedia: MediaFile? = null

    /**
     * An engine calls this once the current file has opened, with its duration in milliseconds
     * when it is known (null or 0 for a live stream). The first call for each media announces it:
     * the offer to continue when watching alone, the file itself in a room. Neither the mode nor
     * the duration decides whether that happens. A later call counts as a duration change.
     */
    fun onEngineFileReady(durationMs: Long?) {
        val media = viewmodel.media ?: return
        if (media === announcedMedia) return onEngineDurationChanged(durationMs)
        publishDuration(media, durationMs)
        announcedMedia = media
        announceFileLoaded()
        // The tracks are known once the file opened, so the language rule runs now, not when a panel opens.
        playerScopeMain.launch {
            runCatching {
                analyzeTracks(media)
                if (viewmodel.media === media) applyTrackRules(media)
            }.onFailure { if (it is CancellationException) throw it else loggy("Track rules failed: ${it.message}") }
        }
    }

    /**
     * An engine calls this when the duration of the current file changes, as when a stream
     * refines its length. A room hears the new duration once the file was announced. It never
     * raises the offer to continue again.
     */
    fun onEngineDurationChanged(durationMs: Long?) {
        val media = viewmodel.media ?: return
        val changed = publishDuration(media, durationMs)
        if (changed && media === announcedMedia && !viewmodel.isSoloMode) announceFileLoaded()
    }

    /** Stores a known duration on [media] and the room's clock. True when [media] had another one. */
    private fun publishDuration(media: MediaFile, durationMs: Long?): Boolean {
        val known = durationMs?.takeIf { it > 0 } ?: return false
        playerManager.timeFullMillis.value = known
        val seconds = known / 1000.0
        if (media.fileDuration == seconds) return false
        media.fileDuration = seconds
        return true
    }

    /**
     * An engine calls this when the current file fails to open or to play. The shared playlist
     * then forgets that it loaded the file, so selecting its entry again loads it again.
     */
    fun onEngineLoadFailed(media: MediaFile? = viewmodel.media) {
        media?.let { viewmodel.playlistManager.onLoadFailed(it) }
    }

    /** Only [onEngineFileReady] and [onEngineDurationChanged] call this, so a file is announced once. */
    private fun announceFileLoaded() {
        // In solo mode (watching alone), a load only offers to resume where this file was left.
        // In a room, the room's position decides instead.
        if (viewmodel.isSoloMode) {
            viewmodel.media?.let { media ->
                viewmodel.resume.onMediaReady(media.fileName, playerManager.timeFullMillis.value)
            }
            return
        }

        viewmodel.media?.let { viewmodel.networkManager.sendAsync(WireMessage.file(it.toFileData())) }
        viewmodel.networkManager.sendAsync(WireMessage.listRequest())
        // The client that loaded the file gets the mismatch warning too, not only the others.
        viewmodel.checkFileMismatches()

        // Re-anchor room sync once per loaded file, now that the engine reports the file as
        // loaded (and so seekable before the next State arrives). Without this, a file that
        // finishes loading after the first server State never adopts the room's position and
        // play state. A load implies media != null, but check anyway: an engine callback on some
        // path could call this before media.value is set.
        if (fileLoadResyncPending && viewmodel.media != null) {
            fileLoadResyncPending = false
            viewmodel.protocol.reanchorSyncOnFileLoad()
        }
    }

    fun onPlaybackEnded() {
        if (!isInitialized) return

        // Auto-advance works the same in a room and in solo mode (the PC client's
        // advanceToNextPlaylistItem has no solo mode and always runs).
        val playlistSize = viewmodel.session.sharedPlaylist.size
        // The PC client advances only when there is more than one item. A single item would repeat
        // only with a loop option, which this app does not have, so a lone item stops at its end.
        if (playlistSize <= 1) return

        val currentIndex = viewmodel.session.spIndex.intValue
        if (currentIndex !in 0 until playlistSize) return

        // Guard against a false end-of-file event (for example a load error that fires "ended" near
        // position 0). Advance only when the media is long enough (the PC client's
        // PLAYLIST_LOAD_NEXT_FILE_MINIMUM_LENGTH = 10 s) and playback is near the end. Otherwise a
        // failed load would skip to the next item.
        val durationMs = playerManager.timeFullMillis.value
        val positionMs = currentPositionMs()
        if (durationMs <= PLAYLIST_ADVANCE_MIN_DURATION_MS) return
        if (durationMs - positionMs > PLAYLIST_ADVANCE_NEAR_END_MS) return

        // At the last item, stop and do not wrap to 0. There is no "loop at end of playlist" option,
        // so wrapping would be wrong (the PC client returns here unless loopAtEndOfPlaylist is on).
        if (currentIndex + 1 >= playlistSize) return

        // Everyone in the room reaches the end of a file at nearly the same moment. Without a
        // debounce, each client sends its own advance and the room skips several entries at once.
        if (viewmodel.playlistManager.justChangedIndex(PLAYLIST_ADVANCE_NEAR_END_MS)) return

        // Only a client that plays the selected entry may advance it. A client that never found
        // the file, or that watches something else, must not move the room to the next entry
        // (the PC client's _notPlayingCurrentIndex).
        if (!viewmodel.playlistManager.isPlayingSelectedEntry) return

        viewmodel.playlistManager.sendPlaylistSelection(currentIndex + 1)
    }

    abstract val trackerJobInterval: Duration

    val shouldTrackTimeManually: Boolean
        get() = trackerJobInterval != 0.seconds

    /** Publishes the engine's position and buffered position while the media is seekable. An engine
     *  whose native position can be unknown may override this to skip the sample, so that a stale
     *  position never gets a new timestamp. */
    protected open suspend fun updatePlaybackProgress() {
        if (isSeekable()) {
            playerManager.samplePosition(currentPositionMs())
            playerManager.timeBufferedMillis.value = bufferedPositionMs() ?: -1L
        }
    }

    private val playerTrackerJob by lazy {
        playerScopeMain.launch {
            while (isActive) {
                updatePlaybackProgress()
                /* The fast interval is only useful while something moves. A paused engine's
                 * position does not change, and estimatedPositionMs() returns the last sample as
                 * is when isNowPlaying is false. A fast poll would then cost a main-thread engine
                 * call each time for nothing (a JNI property read on mpv, an ObjC array bridge on
                 * AVPlayer). Seeks record their own sample, so a paused playhead still moves as
                 * soon as someone drags it.
                 *
                 * Buffering counts as moving. Every engine reports that it is not playing while it
                 * refills, and then the buffered band that this loop updates is the only part of
                 * the screen that changes. */
                val active = playerManager.isNowPlaying.value || playerManager.isBuffering.value
                delay(if (active) trackerJobInterval else IDLE_TRACKER_INTERVAL)
            }
        }
    }

    fun startTrackingProgress() {
        // Reading the lazy playerTrackerJob starts it if it has not started yet.
        if (shouldTrackTimeManually) {
            playerTrackerJob
        }
    }


    open suspend fun reloadVideo() {}
}
