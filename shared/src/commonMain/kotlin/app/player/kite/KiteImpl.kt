package app.player.kite

import androidx.annotation.UiThread
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputComponent
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
import app.preferences.Preferences.KITE_AUDIO_DELAY_MS
import app.preferences.Preferences.KITE_DEBUG_STATS
import app.preferences.Preferences.KITE_EQ_BRIGHTNESS
import app.preferences.Preferences.KITE_EQ_CONTRAST
import app.preferences.Preferences.KITE_EQ_HUE
import app.preferences.Preferences.KITE_EQ_SATURATION
import app.preferences.Preferences.KITE_COMPOSE_RENDERER
import app.preferences.Preferences.KITE_HARDWARE_ACCELERATION
import app.preferences.Preferences.KITE_PRESERVE_PITCH
import app.preferences.Preferences.KITE_SUBTITLE_AUTOSELECT
import app.preferences.Preferences.KITE_SUBTITLE_DELAY_MS
import app.preferences.Preferences.KITE_SUBTITLE_POS
import app.preferences.Preferences.KITE_SUBTITLE_SCALE
import app.preferences.PrefExtraConfig
import app.preferences.settings.SettingCategory
import app.preferences.value
import app.preferences.watchPref
import io.github.yuroyami.kiteplayer.compose.KitePlayerVideo
import io.github.yuroyami.kiteplayer.compose.KiteRenderPath
import app.room.RoomViewmodel
import app.utils.generateTimestampMillis
import app.utils.loggy
import io.github.vinceglb.filekit.PlatformFile
import io.github.yuroyami.kiteplayer.HwdecPolicy
import io.github.yuroyami.kiteplayer.KitePlayer
import io.github.yuroyami.kiteplayer.KitePlayerPlatform
import io.github.yuroyami.kiteplayer.MediaItem
import io.github.yuroyami.kiteplayer.PlaybackStatus
import io.github.yuroyami.kiteplayer.PlayerConfig
import io.github.yuroyami.kiteplayer.SeekMode
import kotlinx.coroutines.CancellationException
import io.github.yuroyami.kiteplayer.SubtitleConfig
import io.github.yuroyami.kiteplayer.SubtitleSource
import io.github.yuroyami.kiteplayer.TrackKind
import io.github.yuroyami.kiteplayer.VideoAdjustments
import io.github.yuroyami.kiteplayer.VideoScale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.uisetting_categ_kite
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

    /** Temporary diagnostic for the background-return video slowdown. Remove once root-caused. */
    private var statsWatcher: Job? = null

    /**
     * KitePlayer publishes position through its own progress flow, but Syncplay's shared tracker
     * reads [currentPositionMs] on a timer, and reading it costs one atomic load. Matching mpv's
     * cadence keeps the room's position reports at the resolution the protocol expects.
     */
    override val trackerJobInterval: Duration = 250.milliseconds

    /** Chapters ride the snapshot since KitePlayer 0.0.5, so the UI may offer chapter jumps. */
    override val supportsChapters: Boolean = true

    /** No gallery-saving frame-grab path is wired yet; captureFrame exists but lands nowhere. */
    override val supportsScreenshot: Boolean = false

    /** Real since KitePlayer 0.0.5: a pitch-preserving tempo stage within 0.25x to 4x. */
    override val supportsSpeedAdjustment: Boolean = true

    /** Android hosts PiP generically; iOS has no KitePlayer-specific PiP controller yet. */
    override val supportsPictureInPicture: Boolean
        get() = KitePlayerPlatform.supportsPictureInPicture

    /** Fit, Fill and Stretch since 0.0.5, cycled in [switchAspectRatio]. */
    override val canChangeAspectRatio: Boolean = true

    @UiThread
    override fun initialize() {
        if (isInitialized) return
        // The engine-level settings read once at creation; the runtime ones (delays, subtitle
        // scale) are ALSO seeded here so a fresh room starts where the sliders sit.
        val config = PlayerConfig(
            hardwareDecode = if (KITE_HARDWARE_ACCELERATION.value()) HwdecPolicy.Auto else HwdecPolicy.Off,
            subtitles = SubtitleConfig(
                autoSelect = KITE_SUBTITLE_AUTOSELECT.value(),
                fontScale = (KITE_SUBTITLE_SCALE.value() / 100f).coerceAtLeast(0.05f),
                delay = KITE_SUBTITLE_DELAY_MS.value().milliseconds,
            ),
        )
        val player = requireNotNull(KitePlayerPlatform.createOrNull(config)) {
            "KitePlayer is unavailable: ${KitePlayerPlatform.availability}"
        }
        player.setAudioDelay(KITE_AUDIO_DELAY_MS.value().milliseconds)
        player.setPreservePitch(KITE_PRESERVE_PITCH.value())
        player.setSubtitlePosition(subtitlePositionFromPref(KITE_SUBTITLE_POS.value()))
        player.setVideoAdjustments(adjustmentsFromPrefs())
        kite = player
        isInitialized = true
        loggy("KitePlayer: engine created")
        startTrackingProgress()
        watchEngineState()
        if (KITE_DEBUG_STATS.value()) watchEngineStats()
    }

    /**
     * The engine-statistics log, now behind the KITE_DEBUG_STATS setting. One line per stats
     * tick (KitePlayer publishes them once a second) tells which layer stalls when playback
     * misbehaves: decodedVideoFrames stalling means the decoder, submittedFrames stalling means
     * the schedule or the renderer refused frames, and both advancing while the screen is
     * static means the frames are drawn by nobody (the Compose/UIKit drawing side). The
     * background-return slowdown investigation reads exactly these lines, so enable the setting
     * before reproducing it on a device.
     */
    private fun watchEngineStats() {
        val player = kite ?: return
        statsWatcher?.cancel()
        statsWatcher = playerScopeMain.launch {
            var lastDecoded = -1L
            var lastSubmitted = -1L
            player.stats.collect { s ->
                if (s.decodedVideoFrames == lastDecoded && s.submittedFrames == lastSubmitted) return@collect
                lastDecoded = s.decodedVideoFrames
                lastSubmitted = s.submittedFrames
                loggy(
                    "KiteStats: status=${player.state.value.status}" +
                        " pos=${player.position().inWholeMilliseconds}" +
                        " decoded=${s.decodedVideoFrames} submitted=${s.submittedFrames}" +
                        " headless=${s.headlessFrames} droppedLate=${s.droppedFramesLate}" +
                        " repeated=${s.repeatedFrames} underruns=${s.audioUnderruns}" +
                        // The three that say WHICH layer is short when playback crawls: a decoder
                        // that cannot keep up shows a low fps with a full video queue, while a
                        // reader that cannot keep up shows both queues near empty and rebuffers
                        // climbing. Without them a crawl looks the same either way.
                        " fps=${s.videoDecodeFps.toInt()} videoQms=${s.videoQueueDepth.inWholeMilliseconds}" +
                        " audioQms=${s.audioQueueDepth.inWholeMilliseconds} rebuffers=${s.rebuffers}" +
                        " drift=${s.avDrift} hwdec=${s.hardwareDecode} master=${s.masterClock}",
                )
            }
        }
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
        statsWatcher?.cancel()
        statsWatcher = null

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

    override suspend fun configurableSettings() = SettingCategory(
        title = Res.string.uisetting_categ_kite,
        icon = Icons.Filled.SettingsInputComponent,
    ) {
        // Creation-time settings: they say so in their summaries and apply at the next load.
        +KITE_HARDWARE_ACCELERATION
        +KITE_SUBTITLE_AUTOSELECT
        // Runtime: flipping it recomposes VideoPlayer, which swaps the presentation over the
        // running player (KitePlayerVideo path change; the engine keeps position and play state).
        +KITE_COMPOSE_RENDERER
        // Runtime settings: the callbacks reach the live engine immediately.
        +KITE_SUBTITLE_SCALE.apply {
            config?.extraConfig = PrefExtraConfig.Slider(maxValue = 300, minValue = 25) { percent ->
                kite?.setSubtitleScale((percent / 100f).coerceAtLeast(0.05f))
            }
        }
        +KITE_SUBTITLE_DELAY_MS.apply {
            config?.extraConfig = PrefExtraConfig.Slider(maxValue = 10_000, minValue = -10_000) { ms ->
                kite?.setSubtitleDelay(ms.milliseconds)
            }
        }
        +KITE_AUDIO_DELAY_MS.apply {
            config?.extraConfig = PrefExtraConfig.Slider(maxValue = 1_000, minValue = -1_000) { ms ->
                kite?.setAudioDelay(ms.milliseconds)
            }
        }
        +KITE_PRESERVE_PITCH.apply {
            config?.extraConfig = PrefExtraConfig.BooleanCallback { preserve ->
                // At 1.0x the two mechanisms are the same bypass; away from it the engine rides
                // its internal precise seek and refuses typed on an unseekable source.
                try {
                    kite?.setPreservePitch(preserve)
                } catch (refused: UnsupportedOperationException) {
                    loggy("KitePlayer: pitch-law change refused: ${refused.message}")
                }
            }
        }
        +KITE_SUBTITLE_POS.apply {
            config?.extraConfig = PrefExtraConfig.Slider(maxValue = 100, minValue = 10) { percent ->
                kite?.setSubtitlePosition(subtitlePositionFromPref(percent))
            }
        }
        +KITE_EQ_BRIGHTNESS.apply {
            config?.extraConfig = PrefExtraConfig.Slider(maxValue = 100, minValue = -100) { _ ->
                kite?.setVideoAdjustments(adjustmentsFromPrefs())
            }
        }
        +KITE_EQ_CONTRAST.apply {
            config?.extraConfig = PrefExtraConfig.Slider(maxValue = 200, minValue = 0) { _ ->
                kite?.setVideoAdjustments(adjustmentsFromPrefs())
            }
        }
        +KITE_EQ_SATURATION.apply {
            config?.extraConfig = PrefExtraConfig.Slider(maxValue = 200, minValue = 0) { _ ->
                kite?.setVideoAdjustments(adjustmentsFromPrefs())
            }
        }
        +KITE_EQ_HUE.apply {
            config?.extraConfig = PrefExtraConfig.Slider(maxValue = 180, minValue = -180) { _ ->
                kite?.setVideoAdjustments(adjustmentsFromPrefs())
            }
        }
        +KITE_DEBUG_STATS.apply {
            config?.extraConfig = PrefExtraConfig.BooleanCallback { enabled ->
                if (enabled) watchEngineStats() else {
                    statsWatcher?.cancel()
                    statsWatcher = null
                }
            }
        }
    }

    /**
     * The four equalizer sliders speak Synkplay's own units (percent-shaped ints); the engine
     * speaks one VideoAdjustments value. Rebuilt whole on every slider move, because the engine
     * bakes the colour matrix once per SETTING, so partial updates would buy nothing.
     */
    private fun adjustmentsFromPrefs() = VideoAdjustments(
        brightness = (KITE_EQ_BRIGHTNESS.value() / 100f).coerceIn(-1f, 1f),
        contrast = (KITE_EQ_CONTRAST.value() / 100f).coerceIn(0f, 2f),
        saturation = (KITE_EQ_SATURATION.value() / 100f).coerceIn(0f, 2f),
        hueDegrees = KITE_EQ_HUE.value().toFloat().coerceIn(-180f, 180f),
    )

    /** The slider says percent from the top of the allowed band; the engine takes a fraction. */
    private fun subtitlePositionFromPref(percent: Int) = (percent / 100f).coerceIn(0.1f, 1f)

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
        try {
            val change = kite?.selectTrack(kind, (track as? KiteTrack)?.trackId)
            loggy("KitePlayer: selectTrack($kind, ${track?.name ?: "none"}) -> $change")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (refused: Exception) {
            // The engine refuses typed (unseekable source, backend without a subtitle decoder,
            // an id the media does not have). Swallowing that here is what made a refused change
            // read as "nothing happens" (owner report 2026-08-26), so it goes to the OSD.
            loggy("KitePlayer: selectTrack($kind) refused: ${refused.message}")
            viewmodel.dispatchOSD { refused.message ?: "Track change refused" }
        }
    }

    override suspend fun analyzeChapters(mediafile: MediaFile) {
        if (!isInitialized) return
        val chapters = kite?.state?.value?.chapters ?: return
        mediafile.chapters.clear()
        chapters.forEachIndexed { index, chapter ->
            mediafile.chapters.add(
                Chapter(
                    index = index,
                    name = chapter.title ?: "Chapter ${index + 1}",
                    timeOffsetMillis = chapter.start.inWholeMilliseconds,
                ),
            )
        }
    }

    override suspend fun jumpToChapter(chapter: Chapter) {
        // The base class broadcasts the seek to the room; the local jump is ours.
        super.jumpToChapter(chapter)
        seekTo(chapter.timeOffsetMillis)
    }

    /**
     * Re-selects whatever the user last chose, after a reload replaced the track list.
     *
     * KitePlayer keeps its selection across a seek and loses it only when new media is opened, and
     * a fresh file has no previous choice to honour, so this is a deliberate no-op rather than an
     * unimplemented hole.
     */
    override suspend fun reapplyTrackChoices() = Unit

    /**
     * Loads a subtitle file into the running engine (KitePlayer 0.0.5): the track appears in the
     * list, is selected immediately, and its cues run through the same timing path container
     * subtitles use. SubRip and WebVTT, the engine's text path.
     *
     * The resolver gives Android's content URIs an openable path, exactly like video; the
     * resolution is released as soon as the call returns, because the engine reads the file once
     * at add time and never again.
     */
    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
        val player = kite ?: return
        val resolved = mediaResolver.resolve(uri)
            ?: error("KitePlayer cannot open the subtitle file $uri")
        try {
            // The video path hands the engine an fd THROUGH its demuxer open options; the
            // subtitle reader is a plain file read with no options channel, so an fd-shaped
            // resolution is respelled as the fd's own filesystem name, which any fopen can
            // read for as long as [resolved] keeps the descriptor alive. iOS resolutions are
            // real paths and skip this entirely.
            val readablePath = resolved.openOptions["fd"]
                ?.let { fd -> "/proc/self/fd/$fd" }
                ?: resolved.uri
            withContext(Dispatchers.IO) {
                val id = player.addExternalSubtitle(SubtitleSource(uri = readablePath))
                loggy("KitePlayer: external subtitle added as $id")
            }
            loggy("KitePlayer: tracks=${player.state.value.tracks}")
            loggy("KitePlayer: warnings=${player.warningHistory().joinToString { it.warning.toString() }}")
        } finally {
            resolved.release()
        }
    }

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

    // TEMP DIAG (pause/seek latency hunt): before/after lines measure how long the engine's
    // transactional pause/play takes to apply. Remove once the wedge is root-caused.
    override suspend fun pause() {
        val t0 = generateTimestampMillis()
        loggy("KiteCmd: pause() commanded, status=${kite?.state?.value?.status}")
        kite?.pause()
        loggy("KiteCmd: pause() applied in ${generateTimestampMillis() - t0}ms, status=${kite?.state?.value?.status}")
    }

    override suspend fun play() {
        val t0 = generateTimestampMillis()
        loggy("KiteCmd: play() commanded, status=${kite?.state?.value?.status}")
        kite?.play()
        loggy("KiteCmd: play() applied in ${generateTimestampMillis() - t0}ms, status=${kite?.state?.value?.status}")
    }

    override suspend fun setSpeed(speed: Double) {
        // Real since 0.0.5: a pitch-preserving tempo stage. The engine refuses a live change on
        // an unseekable source (there is no epoch boundary to ride); the room's 0.95x slowdown
        // then simply does not happen, which is the honest outcome for a live stream.
        try {
            kite?.setSpeed(speed.coerceIn(KitePlayer.SPEED_MIN, KitePlayer.SPEED_MAX))
        } catch (refused: UnsupportedOperationException) {
            loggy("KitePlayer: speed change refused: ${refused.message}")
        }
    }

    override suspend fun isSeekable(): Boolean = kite?.state?.value?.seekable == true

    @UiThread
    override fun seekTo(toPositionMs: Long) {
        if (!isInitialized) return
        super.seekTo(toPositionMs)
        // seekLater is KitePlayer's non-suspending seek: it hands the request to the engine's own
        // seek machine and returns, which is exactly the contract this UiThread member needs.
        // KeyframeThenRefine is mpv's two-phase feel (owner report 2026-08-17): the keyframe at
        // or before the target presents IMMEDIATELY, and the engine decodes forward to the exact
        // frame behind it. Precise here made a bar drag on a long-GOP 3GB file sit on the old
        // picture for the whole decode-forward, which read as the player "reloading". The
        // position mask reports the exact target throughout, so the room protocol and the seek
        // bar never see the intermediate keyframe position.
        loggy("KiteCmd: seekLater(${toPositionMs}ms) fired, status=${kite?.state?.value?.status}") // TEMP DIAG
        kite?.seekLater(toPositionMs.milliseconds, SeekMode.KeyframeThenRefine)
    }

    @UiThread
    override fun currentPositionMs(): Long = kite?.position()?.inWholeMilliseconds ?: 0L

    override suspend fun switchAspectRatio(): String {
        val player = kite ?: return "Fit"
        val next = when (player.state.value.videoScale) {
            VideoScale.Fit -> VideoScale.Fill
            VideoScale.Fill -> VideoScale.Stretch
            VideoScale.Stretch -> VideoScale.Fit
        }
        player.setVideoScale(next)
        return when (next) {
            VideoScale.Fit -> "Fit"
            VideoScale.Fill -> "Fill (crop)"
            VideoScale.Stretch -> "Stretch"
        }
    }

    /**
     * The shared subtitle-size control speaks in the app's own units, 16 being its default; the
     * engine speaks in a multiplier over the authored size. Mapping the two at 16-to-1.0 keeps
     * the one slider meaning the same thing on every engine.
     */
    override suspend fun changeSubtitleSize(newSize: Int) {
        kite?.setSubtitleScale((newSize / 16f).coerceAtLeast(0.05f))
    }

    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        // Construct from the composition that owns the output. KitePlayerVideo reports its
        // renderer attached, so an early media injection waits for the same invariant on the
        // native-view and the pure-Compose path alike. Flipping the pref swaps the presentation
        // over the RUNNING player: the engine rebuilds a coupled decoder at position by itself.
        LaunchedEffect(Unit) {
            initialize()
        }
        val composedKite by kiteFlow.collectAsState()
        val composeRenderer by KITE_COMPOSE_RENDERER.watchPref()
        KitePlayerVideo(
            player = composedKite,
            modifier = modifier,
            path = if (composeRenderer) KiteRenderPath.ComposeCanvas else KiteRenderPath.NativeView,
            onRendererAttached = { presented ->
                if (presented === kiteFlow.value && presentedPlayer.complete(presented)) {
                    onPlayerReady()
                }
            },
        )
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
