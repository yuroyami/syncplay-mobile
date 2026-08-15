package app.player.kite

import androidx.annotation.UiThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.player.PlayerImpl
import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.player.models.Track
import app.preferences.settings.SettingCategory
import app.room.RoomViewmodel
import app.utils.loggy
import io.github.vinceglb.filekit.PlatformFile
import io.github.yuroyami.kiteplayer.KitePlayer
import io.github.yuroyami.kiteplayer.KitePlayerPlatform
import io.github.yuroyami.kiteplayer.MediaItem
import io.github.yuroyami.kiteplayer.PlaybackStatus
import io.github.yuroyami.kiteplayer.SeekMode
import io.github.yuroyami.kiteplayer.TrackKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * KitePlayer driving Syncplay, written once for both phones.
 *
 * There is no androidMain/iosMain split below this line. KitePlayer's own engine is common code,
 * so the whole of Syncplay's player contract maps onto it in shared source; the only per-platform
 * piece is [KiteMediaResolver], which exists because Android hands out `content://` URIs that
 * FFmpeg cannot open and iOS hands out real paths that it can. The platform engine registry
 * injects that resolver once when it constructs [KiteEngine].
 *
 * The suspend contract fits without a single blocking bridge. Everything Syncplay declares as
 * suspend (open, destroy, track selection) is suspend in KitePlayer too, and the two members
 * Syncplay needs synchronously, [currentPositionMs] and [seekTo], map onto KitePlayer's
 * deliberately non-suspending `position()` and `seekLater()`. No `runBlocking` appears here, and
 * none is needed.
 */
internal class KiteImpl(
    viewmodel: RoomViewmodel,
    engine: KiteEngine,
    private val mediaResolver: KiteMediaResolver,
    private val presentation: KitePlayerPresentation,
) : PlayerImpl(viewmodel, engine) {

    /**
     * A StateFlow, not a plain field and deliberately not Compose snapshot state. Not plain
     * because [VideoPlayer] passes it to its presentation and [initialize] assigns it from inside
     * that composable's first pass; a plain `var` would be read once as null and never re-read,
     * leaving video output permanently detached from a player that is otherwise running. Not
     * `mutableStateOf` because this impl is constructed on RoomViewmodel's IO launch, and a
     * snapshot-state object created off the main thread can be read by a composition whose
     * snapshot predates that creation; both iOS presentations crashed on room entry with
     * "Reading a state that was created after the snapshot was taken". A StateFlow has no
     * snapshot identity, so the construction thread cannot matter.
     */
    private val kiteFlow = MutableStateFlow<KitePlayer?>(null)

    private var kite: KitePlayer?
        get() = kiteFlow.value
        set(value) {
            kiteFlow.value = value
        }

    /**
     * Completed only after the active presentation has attached video output to [kite]. Incoming
     * room media can race the first composition; suspending it here preserves the load and ensures
     * the renderer is present before KitePlayer chooses its decoder path.
     */
    private val presentedPlayer = CompletableDeferred<KitePlayer>()

    /**
     * The resolution behind the media currently loaded, kept alive for exactly as long as the
     * engine may read from it. Released when the next media replaces it or the player is torn
     * down; on Android that release closes a file descriptor, on iOS it does nothing.
     */
    private var mediaPath: KiteMediaPath? = null

    /** KitePlayer publishes a duration once the container is parsed, so nothing is polled for it. */
    private var durationWatcher: Job? = null

    /**
     * KitePlayer publishes position through its own progress flow, but Syncplay's shared tracker
     * reads [currentPositionMs] on a timer, and reading it costs one atomic load. Matching mpv's
     * cadence keeps the room's position reports at the resolution the protocol expects.
     */
    override val trackerJobInterval: Duration = 250.milliseconds

    /** The engine reads chapters from no container yet, so the UI must not offer chapter jumps. */
    override val supportsChapters: Boolean = false

    /** No frame-grab path is exposed on the facade yet. */
    override val supportsScreenshot: Boolean = false

    /** KitePlayer 0.0.3 deliberately supports 1.0x only. */
    override val supportsSpeedAdjustment: Boolean = false

    /** Android hosts PiP generically; iOS has no KitePlayer-specific PiP controller yet. */
    override val supportsPictureInPicture: Boolean
        get() = KitePlayerPlatform.supportsPictureInPicture

    /**
     * KitePlayer does not stretch the picture: it presents at the source's own aspect and letterboxes
     * inside whatever box it is given, so there is no ratio to cycle through.
     */
    override val canChangeAspectRatio: Boolean = false

    @UiThread
    override fun initialize() {
        if (isInitialized) return
        kite = requireNotNull(KitePlayerPlatform.createOrNull()) {
            "KitePlayer is unavailable: ${KitePlayerPlatform.availability}"
        }
        isInitialized = true
        loggy("KitePlayer: engine created")
        startTrackingProgress()
        watchEngineState()
    }

    /**
     * KitePlayer knows a file's real duration the moment its container is parsed and publishes it
     * on the snapshot flow, so the room is announced from that event rather than from
     * [parseMedia]. Declaring it here is what stops the iOS path announcing a second time with no
     * duration attached.
     */
    override val announcesFileLoadViaEvent: Boolean = true

    /**
     * Mirrors the engine's own state onto the room: the play/pause truth every engine owes
     * [app.player.PlayerManager.isNowPlaying], the duration the container reported, the file
     * announcement that re-anchors sync, and the end of playback that drives shared-playlist
     * advance. All of it comes from the snapshot flow rather than from polling, which is why
     * [trackerJobInterval] only has to carry the position.
     */
    private fun watchEngineState() {
        val player = kite ?: return
        durationWatcher?.cancel()
        durationWatcher = playerScopeMain.launch {
            var wasEnded = false
            var announcedMedia: MediaFile? = null
            player.state.collect { snapshot ->
                // The play button and the protocol's divergence broadcast both collect
                // isNowPlaying, so the engine's status must be mirrored the way every other
                // engine mirrors its events. Buffering counts as playing: the engine is trying
                // to advance (isActive), and KitePlayer never auto-pauses on underrun, so a
                // buffering spell must not read as a pause. Opening is media lifecycle, not a
                // playback intent, and is left alone like VLCKit's transitional states.
                when (snapshot.status) {
                    PlaybackStatus.Playing, PlaybackStatus.Buffering ->
                        playerManager.isNowPlaying.value = true
                    PlaybackStatus.Paused, PlaybackStatus.Ended,
                    PlaybackStatus.Idle, PlaybackStatus.Failed ->
                        playerManager.isNowPlaying.value = false
                    PlaybackStatus.Opening -> Unit
                }
                val durationMs = snapshot.duration?.inWholeMilliseconds ?: 0L
                if (durationMs > 0) {
                    playerManager.timeFullMillis.value = durationMs

                    // Announce every new MediaFile once even when a URL resolver already supplied
                    // the same duration. Duration equality is not file identity: using it as the
                    // only guard skipped the room re-anchor entirely for resolved streams. A later
                    // HLS/DASH duration refinement is announced again for the same file.
                    val durationSeconds = durationMs / 1000.0
                    val media = viewmodel.media
                    val durationChanged = media?.fileDuration != durationSeconds
                    if (durationChanged) media?.fileDuration = durationSeconds
                    if (media != null && (media !== announcedMedia || durationChanged)) {
                        announcedMedia = media
                        announceFileLoaded()
                    }
                }

                val ended = snapshot.status == PlaybackStatus.Ended
                if (ended && !wasEnded) onPlaybackEnded()
                wasEnded = ended
            }
        }
    }

    override suspend fun destroy() {
        // Match every other engine's destroy contract: stop all RoomViewmodel-capturing jobs
        // before the player disappears, then finish native teardown even if the caller is cancelled.
        isInitialized = false
        playerSupervisorJob.cancel()
        presentedPlayer.cancel()
        durationWatcher?.cancel()
        durationWatcher = null

        // Cancellation above is still required when teardown races an injection waiting for the
        // first video output; only resource teardown itself can be skipped in the empty state.
        if (kite == null && mediaPath == null) return

        val player = kite
        kite = null
        val path = mediaPath
        mediaPath = null

        withContext(NonCancellable) {
            try {
                player?.closeAndAwait()
            } finally {
                path?.release()
            }
        }
    }

    override fun onClosing() {
        // An early load can be suspended waiting for the first applied native surface while the
        // base teardown is waiting for the media transaction mutex. Wake it before that wait.
        presentedPlayer.cancel()
        playerSupervisorJob.cancel()
    }

    /** Nothing to tune yet: the engine exposes no per-engine options through its facade. */
    override suspend fun configurableSettings(): SettingCategory? = null

    override suspend fun hasMedia(): Boolean =
        isInitialized && kite?.state?.value?.media != null

    override suspend fun isPlaying(): Boolean =
        kite?.state?.value?.status?.isActive == true

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return
        val tracks = kite?.state?.value?.tracks ?: return
        mediafile.tracks.clear()

        tracks.audio.forEachIndexed { position, info ->
            mediafile.tracks.add(
                KiteTrack(
                    name = info.label,
                    type = TrackType.AUDIO,
                    index = position,
                    selected = info.id == tracks.selectedAudio,
                    trackId = info.id,
                ),
            )
        }
        tracks.subtitles.forEachIndexed { position, info ->
            mediafile.tracks.add(
                KiteTrack(
                    name = info.label,
                    type = TrackType.SUBTITLE,
                    index = position,
                    selected = info.id == tracks.selectedSubtitle,
                    trackId = info.id,
                ),
            )
        }
    }

    override suspend fun selectTrack(track: Track?, type: TrackType) {
        if (!isInitialized) return
        val kind = when (type) {
            TrackType.AUDIO -> TrackKind.Audio
            TrackType.SUBTITLE -> TrackKind.Subtitle
        }
        // A null track means "none", which the engine spells as a null id. Anything that is not
        // one of ours cannot be resolved to a stream, so it is treated the same way rather than
        // guessed at.
        kite?.selectTrack(kind, (track as? KiteTrack)?.trackId)
    }

    /** No source reads a chapter list out of a container yet, so there is nothing to analyse. */
    override suspend fun analyzeChapters(mediafile: MediaFile) = Unit

    override suspend fun jumpToChapter(chapter: Chapter) = Unit

    /**
     * Re-selects whatever the user last chose, after a reload replaced the track list.
     *
     * KitePlayer keeps its selection across a seek and loses it only when new media is opened, and
     * a fresh file has no previous choice to honour, so this is a deliberate no-op rather than an
     * unimplemented hole.
     */
    override suspend fun reapplyTrackChoices() = Unit

    /**
     * External subtitle files are not loadable yet: the facade takes them only as part of the
     * media item at open time, and nothing consumes them there. The engine's own subtitle work is
     * limited to SubRip and WebVTT tracks already inside the container.
     */
    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) = Unit

    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
        val player = awaitPresentedPlayer()
        val resolved = mediaResolver.resolve(location.file)
        if (resolved == null) {
            loggy("KitePlayer: no openable path for ${location.commonUri}")
            error("KitePlayer cannot open ${location.commonUri}")
        }
        openAndKeep(player, resolved)
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        val player = awaitPresentedPlayer()
        openAndKeep(player, kiteMediaPathOf(location.url))
    }

    /** Waits for player construction and video-output attachment instead of dropping an early load. */
    private suspend fun awaitPresentedPlayer(): KitePlayer = presentedPlayer.await()

    /**
     * Opens [path] and only then releases the previous one. The order matters on Android: the old
     * resolution may own a file descriptor the engine is still reading from while the new open
     * probes its container, and closing it first would pull the floor out from under a running
     * demuxer.
     */
    private suspend fun openAndKeep(player: KitePlayer, path: KiteMediaPath) {
        val previous = mediaPath
        mediaPath = path
        loggy("KitePlayer: opening ${path.uri} options=${path.openOptions}")
        try {
            withContext(Dispatchers.IO) {
                // KitePlayer's open() is strict: legal only from Idle, Ended and Failed, and a
                // second file loaded while the first sits Paused throws. stop() is legal from
                // EVERY state (a no-op when there is nothing to stop), so the unconditional
                // prefix is the correct caller-side spelling of "replace whatever is playing".
                player.stop()
                player.open(MediaItem(uri = path.uri, openOptions = path.openOptions))
            }
            loggy("KitePlayer: opened, status=${player.state.value.status} duration=${player.state.value.duration}")
        } catch (e: Exception) {
            // PlayerImpl.inject catches this and shows the load-failure OSD; the line here is what
            // says WHICH uri and WHY, which the OSD cannot.
            loggy("KitePlayer: open failed for ${path.uri}: ${e.stackTraceToString()}")
            throw e
        } finally {
            previous?.release()
        }
    }

    override suspend fun pause() {
        kite?.pause()
    }

    override suspend fun play() {
        kite?.play()
    }

    override suspend fun setSpeed(speed: Double) {
        // The shared sync policy checks supportsSpeedAdjustment before requesting 0.95x. Keep
        // this defensive guard because KitePlayer correctly throws for unsupported rates.
        if (speed == 1.0) kite?.setSpeed(speed)
    }

    override suspend fun isSeekable(): Boolean = kite?.state?.value?.seekable == true

    @UiThread
    override fun seekTo(toPositionMs: Long) {
        if (!isInitialized) return
        super.seekTo(toPositionMs)
        // seekLater is KitePlayer's non-suspending seek: it hands the request to the engine's own
        // seek machine and returns, which is exactly the contract this UiThread member needs.
        kite?.seekLater(toPositionMs.milliseconds, SeekMode.Precise)
    }

    @UiThread
    override fun currentPositionMs(): Long = kite?.position()?.inWholeMilliseconds ?: 0L

    override suspend fun switchAspectRatio(): String = "Fit"

    /**
     * Subtitle sizing is not adjustable yet. The engine rasterises cues at a size it chooses, and
     * the facade exposes no scale, so accepting the value silently would be a lie.
     */
    override suspend fun changeSubtitleSize(newSize: Int) = Unit

    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        // Construct from the composition that owns the output. The presentation publishes
        // readiness only after its surface/renderer is attached, so an early media injection waits
        // for the same invariant whether this is the native-view or a pure-Compose path.
        LaunchedEffect(Unit) {
            initialize()
        }
        val composedKite by kiteFlow.collectAsState()
        presentation.Content(player = composedKite, modifier = modifier) { presented ->
            if (presented === kiteFlow.value && presentedPlayer.complete(presented)) {
                onPlayerReady()
            }
        }
    }

    /** KitePlayer owns its own output volume; there is no system stream to read here. */
    override fun getMaxVolume(): Int = MAX_VOLUME

    override fun getCurrentVolume(): Int =
        ((kite?.state?.value?.volume ?: 1f) * MAX_VOLUME).toInt().coerceIn(0, MAX_VOLUME)

    override fun changeCurrentVolume(v: Int) {
        kite?.setVolume(v.coerceIn(0, MAX_VOLUME) / MAX_VOLUME.toFloat())
    }

    private companion object {
        /** A 0..100 scale, so the shared volume slider has the resolution it expects. */
        const val MAX_VOLUME = 100
    }
}
