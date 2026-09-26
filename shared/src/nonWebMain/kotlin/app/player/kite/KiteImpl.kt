@file:OptIn(AudioVizAuthoringApi::class)

package app.player.kite

import androidx.annotation.UiThread
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.i18n.Localization
import app.player.PlayerImpl
import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.player.models.Track
import app.player.models.TrackTrait
import app.preferences.Preferences.KITE_AUDIO_DELAY_MS
import app.preferences.Preferences.AUDIO_VISUALIZATION
import app.preferences.Preferences.KITE_AUDIO_VIZ_DIRECTOR
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
import app.preferences.Preferences.SUBTITLE_SIZE
import app.preferences.PrefExtraConfig
import app.preferences.settings.SettingCategory
import app.preferences.settings.withControl
import app.preferences.value
import app.preferences.watchPref
import io.github.yuroyami.kiteplayer.audioviz.KiteAudioViz
import app.player.models.VisualizerControls
import app.player.models.shouldShowAudioVisualization
import app.preferences.set
import io.github.yuroyami.kiteplayer.audioviz.AudioVizAuthoringApi
import io.github.yuroyami.kiteplayer.audioviz.AudioVizState
import io.github.yuroyami.kiteplayer.audioviz.SongMapStore
import io.github.yuroyami.kiteplayer.audioviz.SongScanPolicy
import io.github.yuroyami.kiteplayer.audioviz.rememberAudioVizState
import io.github.yuroyami.kiteplayer.audioviz.viz.Visualization
import io.github.yuroyami.kiteplayer.compose.KitePlayerVideo
import io.github.yuroyami.kiteplayer.compose.KiteRenderPath
import app.room.OSDCategory
import app.room.RoomViewmodel
import app.uicomponents.glassEnabled
import app.utils.getCacheDirectoryPath
import app.utils.getFileName
import app.utils.loggy
import app.utils.writeFileBytes
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import io.github.yuroyami.kiteplayer.HwdecPolicy
import io.github.yuroyami.kiteplayer.KitePlayer
import io.github.yuroyami.kiteplayer.KitePlayerPlatform
import io.github.yuroyami.kiteplayer.MediaItem
import io.github.yuroyami.kiteplayer.PlaybackError
import io.github.yuroyami.kiteplayer.PlaybackStatus
import io.github.yuroyami.kiteplayer.PlayerConfig
import io.github.yuroyami.kiteplayer.SeekMode
import kotlinx.coroutines.CancellationException
import io.github.yuroyami.kiteplayer.SubtitleConfig
import io.github.yuroyami.kiteplayer.SubtitleSource
import io.github.yuroyami.kiteplayer.TrackInfo
import io.github.yuroyami.kiteplayer.TrackKind
import io.github.yuroyami.kiteplayer.VideoAdjustments
import io.github.yuroyami.kiteplayer.VideoScale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The KitePlayer engine (one of the video players the app can drive), written once for Android,
 * iOS and desktop.
 *
 * This file has no per-platform split. KitePlayer's own engine is common code, so Syncplay's whole
 * player contract maps onto it in shared source. The only per-platform piece is
 * [KiteMediaResolver]: Android hands out `content://` URIs that FFmpeg cannot open, while iOS and
 * desktop hand out real paths that it can. Each platform's engine list passes that resolver in
 * when it constructs [KiteEngine].
 *
 * The suspend contract fits without a blocking bridge. Everything Syncplay declares as suspend
 * (open, destroy, track selection) is suspend in KitePlayer too. The two members that Syncplay
 * needs synchronously, [currentPositionMs] and [seekTo], map onto KitePlayer's non-suspending
 * `position()` and `seekLater()`. No `runBlocking` is needed here.
 */
internal class KiteImpl(
    viewmodel: RoomViewmodel,
    private val kiteEngine: KiteEngine,
    private val mediaResolver: KiteMediaResolver,
) : PlayerImpl(viewmodel, kiteEngine) {

    /**
     * A StateFlow, not a plain field, and on purpose not Compose snapshot state.
     *
     * Not a plain field, because [VideoPlayer] passes it to its presentation and [initialize]
     * assigns it inside that composable's first pass. A plain `var` is read once as null and never
     * again, so video output stays detached from a player that is otherwise running.
     *
     * Not `mutableStateOf`, because this impl is constructed on RoomViewmodel's IO launch. A
     * composition whose snapshot is older than a snapshot-state object created off the main
     * thread can still read that object, and on iOS that crashes room entry with "Reading a state
     * that was created after the snapshot was taken". A StateFlow has no snapshot identity, so
     * the construction thread does not matter.
     */
    private val kiteFlow = MutableStateFlow<KitePlayer?>(null)

    /** Set by the video composable while the visualizer state exists, cleared when it goes. */
    private val visualizerFlow = MutableStateFlow<VisualizerControls?>(null)
    override val visualizer: StateFlow<VisualizerControls?> get() = visualizerFlow

    private var kite: KitePlayer?
        get() = kiteFlow.value
        set(value) {
            kiteFlow.value = value
        }

    /**
     * Completed only after the active presentation has attached video output to [kite]. Media from
     * the room can arrive before the first composition. Suspending the load here keeps it, and
     * makes sure the renderer exists before KitePlayer chooses its decoder path.
     */
    private val presentedPlayer = CompletableDeferred<KitePlayer>()

    /**
     * The resolution behind the media currently loaded, kept alive exactly as long as the engine
     * may read from it. It is released when the next media replaces it or the player is torn
     * down. On Android that release closes a file descriptor; on iOS and desktop it does nothing.
     */
    private var mediaPath: KiteMediaPath? = null

    /**
     * The job that mirrors the engine's state flow (see [watchEngineState]). KitePlayer publishes
     * a duration once the container is parsed, so nothing is polled for it.
     */
    private var durationWatcher: Job? = null

    /** The engine-statistics log, active while KITE_DEBUG_STATS is on (see [watchEngineStats]). */
    private var statsWatcher: Job? = null

    /**
     * KitePlayer publishes the position through its own progress flow, but Syncplay's shared
     * tracker reads [currentPositionMs] on a timer, and one read costs one atomic load. The same
     * interval as mpv keeps the room's position reports at the resolution the protocol expects.
     */
    override val trackerJobInterval: Duration = 250.milliseconds

    override val supportsVideoTrackSelection = true
    override val supportsAudioVisualization = true

    /** KitePlayer's state snapshot carries the chapters, so the UI may offer chapter jumps. */
    override val supportsChapters: Boolean = true

    /** A pitch-preserving tempo stage from 0.25x to 4x. */
    override val supportsSpeedAdjustment: Boolean = true

    /** Android hosts PiP (picture-in-picture) generically; iOS has no KitePlayer PiP controller. */
    override val supportsPictureInPicture: Boolean
        get() = KitePlayerPlatform.supportsPictureInPicture

    /** Fit, Fill and Stretch, cycled in [switchAspectRatio]; every renderer follows the mode. */
    override val canChangeAspectRatio: Boolean = true

    @UiThread
    override fun initialize() {
        if (isInitialized) return
        // The engine-level settings are read once, at creation. The runtime ones (delays, subtitle
        // scale) are also set here, so a new room starts where the sliders are.
        val config = PlayerConfig(
            hardwareDecode = if (KITE_HARDWARE_ACCELERATION.value()) HwdecPolicy.Auto else HwdecPolicy.Off,
            subtitles = SubtitleConfig(
                autoSelect = KITE_SUBTITLE_AUTOSELECT.value(),
                // The shared subtitle-size setting, on the 16-to-1.0 scale of changeSubtitleSize.
                fontScale = (SUBTITLE_SIZE.value() / 16f).coerceAtLeast(0.05f),
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
     * Logs engine statistics while the KITE_DEBUG_STATS setting is on. KitePlayer publishes stats
     * once a second, and each line shows which layer stalls when playback misbehaves:
     *  - decodedVideoFrames stops: the decoder stalls.
     *  - submittedFrames stops: the schedule or the renderer refuses frames.
     *  - Both advance while the screen is static: nothing draws the frames (the Compose or UIKit
     *    drawing side).
     *
     * To diagnose a video slowdown after a return from the background, turn the setting on
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
                        // These three show which layer falls behind when playback is slow. A
                        // decoder that cannot keep up shows a low fps with a full video queue. A
                        // reader that cannot keep up shows both queues near empty and a rising
                        // rebuffer count. Without them, both cases look the same.
                        " fps=${s.videoDecodeFps.toInt()} videoQms=${s.videoQueueDepth.inWholeMilliseconds}" +
                        " audioQms=${s.audioQueueDepth.inWholeMilliseconds} rebuffers=${s.rebuffers}" +
                        " drift=${s.avDrift} hwdec=${s.hardwareDecode} master=${s.masterClock}",
                )
            }
        }
    }

    /**
     * KitePlayer knows a file's real duration as soon as its container is parsed, and publishes it
     * on the snapshot flow. So the file is announced to the room from that event, not from
     * [parseMedia]. Without this flag, the iOS path announces the file a second time with no
     * duration.
     */
    override val announcesFileLoadViaEvent: Boolean = true

    /**
     * Mirrors the engine's state onto the room: the play/pause state that every engine reports
     * through [app.player.PlayerManager.isNowPlaying], the duration from the container, the file
     * announcement that re-anchors sync, and the end of playback that advances the shared
     * playlist (the file list that everyone in the room follows). All of it comes from the
     * snapshot flow, not from polling, so [trackerJobInterval] only carries the position.
     */
    private fun watchEngineState() {
        val player = kite ?: return
        durationWatcher?.cancel()
        durationWatcher = playerScopeMain.launch {
            var wasEnded = false
            var reportedError: PlaybackError? = null
            player.state.collect { snapshot ->
                // The play button and the protocol's divergence broadcast both collect
                // isNowPlaying, so mirror the engine's status as every other engine mirrors its
                // events. See mirrorOf for what each status means for the room.
                val mirror = mirrorOf(snapshot.status)
                playerManager.isBuffering.value = mirror.buffering
                // Marked before the stop shows, so the room is not told about it.
                if (mirror.ownStop) viewmodel.protocol.noteExpectedPlaybackState(paused = true)
                mirror.playing?.let { playerManager.isNowPlaying.value = it }
                if (snapshot.status == PlaybackStatus.Failed) {
                    val error = snapshot.error
                    if (error != null && error !== reportedError) {
                        reportedError = error
                        val reason = error.message
                        viewmodel.dispatchOSD(OSDCategory.WARNING) { Localization.strings.roomPlaybackError(reason) }
                        viewmodel.dispatcher.broadcastMessage(isChat = false, isError = true) {
                            Localization.strings.roomPlaybackError(reason)
                        }
                        onEngineLoadFailed()
                    }
                }
                val durationMs = snapshot.duration?.inWholeMilliseconds ?: 0L
                if (durationMs > 0) playerManager.timeFullMillis.value = durationMs

                // The first opened snapshot of a file announces it, with whatever duration is
                // known (a live stream has none). A later HLS or DASH refinement only updates the
                // room, and never raises the offer to continue again.
                val opened = snapshot.status != PlaybackStatus.Idle && snapshot.status != PlaybackStatus.Opening
                if (viewmodel.media != null && opened && snapshot.status != PlaybackStatus.Failed) {
                    onEngineFileReady(durationMs)
                }

                val ended = snapshot.status == PlaybackStatus.Ended
                if (ended && !wasEnded) onPlaybackEnded()
                wasEnded = ended
            }
        }
    }

    override suspend fun destroy() {
        // The destroy contract that every engine follows (checked by checkDestroyContract): stop
        // every job that captures RoomViewmodel before the player goes away, then finish the
        // native teardown even if the caller is cancelled.
        isInitialized = false
        playerSupervisorJob.cancel()
        presentedPlayer.cancel()
        durationWatcher?.cancel()
        durationWatcher = null
        statsWatcher?.cancel()
        statsWatcher = null

        // The cancellation above is still needed when teardown races a load that waits for the
        // first video output. Only the resource teardown can be skipped in the empty state.
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
        // An early load can be suspended, waiting for the first video output, while the base
        // teardown waits for the media transaction mutex. Wake the load before that wait.
        presentedPlayer.cancel()
        playerSupervisorJob.cancel()
    }

    override suspend fun configurableSettings() = SettingCategory(
        key = "engine-kite",
        title = { it.uisettingCategKite },
        icon = Icons.Filled.SettingsInputComponent,
    ) {
        // Creation-time settings: their summaries say so, and they apply at the next load.
        +KITE_HARDWARE_ACCELERATION
        +KITE_SUBTITLE_AUTOSELECT
        // Runtime: a change recomposes VideoPlayer, which swaps the presentation over the running
        // player (a KitePlayerVideo path change; the engine keeps position and play state).
        +KITE_COMPOSE_RENDERER
        // Runtime settings: the callbacks reach the live engine at once. Subtitle size is the
        // shared player setting (SUBTITLE_SIZE); a second slider here would fight it on every
        // file load.
        +KITE_SUBTITLE_DELAY_MS.withControl(PrefExtraConfig.Slider(maxValue = 10_000, minValue = -10_000) { ms ->
                kite?.setSubtitleDelay(ms.milliseconds)
            })
        +KITE_AUDIO_DELAY_MS.withControl(PrefExtraConfig.Slider(maxValue = 1_000, minValue = -1_000) { ms ->
                kite?.setAudioDelay(ms.milliseconds)
            })
        +KITE_PRESERVE_PITCH.withControl(PrefExtraConfig.BooleanCallback { preserve ->
                // At 1.0x both pitch modes are the same bypass. At other speeds the engine
                // switches through an internal precise seek, and on an unseekable source it
                // refuses with UnsupportedOperationException.
                try {
                    kite?.setPreservePitch(preserve)
                } catch (refused: UnsupportedOperationException) {
                    loggy("KitePlayer: pitch-law change refused: ${refused.message}")
                }
            })
        +KITE_SUBTITLE_POS.withControl(PrefExtraConfig.Slider(maxValue = 100, minValue = 10) { percent ->
                kite?.setSubtitlePosition(subtitlePositionFromPref(percent))
            })
        +KITE_EQ_BRIGHTNESS.withControl(PrefExtraConfig.Slider(maxValue = 100, minValue = -100) { _ ->
                kite?.setVideoAdjustments(adjustmentsFromPrefs())
            })
        +KITE_EQ_CONTRAST.withControl(PrefExtraConfig.Slider(maxValue = 200, minValue = 0) { _ ->
                kite?.setVideoAdjustments(adjustmentsFromPrefs())
            })
        +KITE_EQ_SATURATION.withControl(PrefExtraConfig.Slider(maxValue = 200, minValue = 0) { _ ->
                kite?.setVideoAdjustments(adjustmentsFromPrefs())
            })
        +KITE_EQ_HUE.withControl(PrefExtraConfig.Slider(maxValue = 180, minValue = -180) { _ ->
                kite?.setVideoAdjustments(adjustmentsFromPrefs())
            })
        +KITE_DEBUG_STATS.withControl(PrefExtraConfig.BooleanCallback { enabled ->
                if (enabled) watchEngineStats() else {
                    statsWatcher?.cancel()
                    statsWatcher = null
                }
            })
    }

    /**
     * Builds the engine's VideoAdjustments from the four equalizer sliders, which use percent-like
     * ints. The value is rebuilt whole on every slider move, because the engine computes the
     * colour matrix once per setting call, so partial updates would save nothing.
     */
    private fun adjustmentsFromPrefs() = VideoAdjustments(
        brightness = (KITE_EQ_BRIGHTNESS.value() / 100f).coerceIn(-1f, 1f),
        contrast = (KITE_EQ_CONTRAST.value() / 100f).coerceIn(0f, 2f),
        saturation = (KITE_EQ_SATURATION.value() / 100f).coerceIn(0f, 2f),
        hueDegrees = KITE_EQ_HUE.value().toFloat().coerceIn(-180f, 180f),
    )

    /** The slider gives a percent from the top of the allowed band; the engine takes a fraction. */
    private fun subtitlePositionFromPref(percent: Int) = (percent / 100f).coerceIn(0.1f, 1f)

    override suspend fun hasMedia(): Boolean =
        isInitialized && kite?.state?.value?.media != null

    override suspend fun isPlaying(): Boolean =
        kite?.state?.value?.status?.isActive == true

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return
        val tracks = kite?.state?.value?.tracks ?: return
        mediafile.tracks.clear()

        tracks.video.filterNot { it.isCoverArt }.forEachIndexed { position, info ->
            mediafile.tracks.add(KiteTrack(
                name = info.title ?: info.codec,
                type = TrackType.VIDEO,
                index = position,
                selected = info.id == tracks.selectedVideo,
                trackId = info.id,
                codec = info.codec,
                videoDescription = info.videoSize?.let { "${it.width} × ${it.height}" },
            ))
        }
        tracks.audio.forEachIndexed { position, info ->
            mediafile.tracks.add(
                KiteTrack(
                    name = info.title?.takeIf { it.isNotBlank() } ?: info.language ?: info.codec,
                    type = TrackType.AUDIO,
                    index = position,
                    selected = info.id == tracks.selectedAudio,
                    trackId = info.id,
                    language = info.language,
                    trait = info.traitOrNull(),
                    channelCount = info.channels,
                    codec = info.codec,
                ),
            )
        }
        tracks.subtitles.forEachIndexed { position, info ->
            mediafile.tracks.add(
                KiteTrack(
                    name = info.title?.takeIf { it.isNotBlank() } ?: info.language ?: info.codec,
                    type = TrackType.SUBTITLE,
                    index = position,
                    selected = info.id == tracks.selectedSubtitle,
                    trackId = info.id,
                    language = info.language,
                    trait = info.traitOrNull(),
                    channelCount = info.channels,
                    codec = info.codec,
                ),
            )
        }
    }

    /** KitePlayer sets both flags on the track itself, so nothing is read from the label. */
    private fun TrackInfo.traitOrNull(): TrackTrait? = when {
        isAccessibility -> TrackTrait.ACCESSIBILITY
        isForced -> TrackTrait.FORCED
        else -> null
    }

    override suspend fun selectTrack(track: Track?, type: TrackType) {
        if (!isInitialized) return
        val kind = when (type) {
            TrackType.VIDEO -> TrackKind.Video
            TrackType.AUDIO -> TrackKind.Audio
            TrackType.SUBTITLE -> TrackKind.Subtitle
        }
        // The language pass in analyzeTracks skips a type that has a recorded pick. Without this
        // record it selects the preferred-language track again right after every pick, "off"
        // included.
        playerManager.currentTrackChoices.remember(type, track)
        // A null track means "none", which the engine expresses as a null id. A track that is not
        // a KiteTrack cannot be resolved to a stream, so it is treated the same way instead of
        // being guessed at.
        try {
            val change = kite?.selectTrack(kind, (track as? KiteTrack)?.trackId)
            loggy("KitePlayer: selectTrack($kind, ${track?.name ?: "none"}) -> $change")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (refused: Exception) {
            // The engine refuses with a typed exception (an unseekable source, a backend without
            // a subtitle decoder, an id the media does not have). A silent failure reads as
            // "nothing happens", so a warning shows. The engine's own reason goes to the log.
            loggy("KitePlayer: selectTrack($kind) refused: ${refused.message}")
            viewmodel.dispatchWarning { Localization.strings.roomTrackChangeRefused }
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
        // The base class broadcasts the seek to the room; this override makes the local jump.
        super.jumpToChapter(chapter)
        seekTo(chapter.timeOffsetMillis)
    }

    /**
     * Re-selects whatever the user last chose, after a reload replaced the track list.
     *
     * KitePlayer keeps its selection across a seek and loses it only when new media is opened, and
     * a new file has no previous choice to restore. So this is a no-op on purpose, not a missing
     * implementation.
     */
    override suspend fun reapplyTrackChoices() = Unit

    /**
     * Loads a subtitle file into the running engine: the track appears in the list, is selected at
     * once, and its cues use the same timing path as container subtitles. The engine's text path
     * reads SubRip and WebVTT.
     *
     * The resolver makes Android's content URIs openable, as for video. The resolution is released
     * as soon as the call returns, because the engine reads the file once, when it adds the track.
     */
    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
        val player = kite ?: return
        val resolved = mediaResolver.resolve(uri)
            ?: error("KitePlayer cannot open the subtitle file $uri")
        try {
            // The video path passes the engine an fd through its demuxer open options. The
            // subtitle reader is a plain file read with no options, and the kernel refuses to
            // reopen a SAF descriptor by its /proc path (the Android resolver explains why). So an
            // fd resolution is copied once into the app cache, and the engine reads the copy. iOS
            // and desktop resolutions are real paths and skip this step.
            val readablePath = if (resolved.openOptions.containsKey("fd")) {
                val dir = getCacheDirectoryPath("subtitles") ?: error("no cache directory for subtitles")
                val name = (getFileName(uri) ?: "subtitle.$extension").substringAfterLast('/')
                val copy = "$dir/$name"
                withContext(Dispatchers.IO) { writeFileBytes(copy, uri.readBytes()) }
                copy
            } else resolved.uri
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

    /**
     * Waits until the player exists and its video output is attached, instead of dropping an early
     * load. The wait has a time limit: a renderer that never attaches must fail the load, not hold
     * the media transaction mutex forever.
     */
    private suspend fun awaitPresentedPlayer(): KitePlayer =
        withTimeoutOrNull(RENDERER_ATTACH_TIMEOUT_MS) { presentedPlayer.await() }
            ?: error("KitePlayer video output did not attach in time")

    /**
     * Opens [path] and only then releases the previous one. The order matters on Android: the old
     * resolution may own a file descriptor that the engine still reads while the new open probes
     * its container. Closing it first would break a running demuxer.
     */
    private suspend fun openAndKeep(player: KitePlayer, path: KiteMediaPath) {
        val previous = mediaPath
        mediaPath = path
        loggy("KitePlayer: opening ${path.uri} options=${path.openOptions}")
        try {
            // The stop below drops the engine to Idle, which mirrors as "not playing". This client
            // caused it, so it is not news for the room: note it before the engine can report it,
            // or every file switch broadcasts a pause to the whole room. The room's real state
            // comes back with the first sync after the new file announces itself.
            viewmodel.protocol.noteExpectedPlaybackState(paused = true)
            withContext(Dispatchers.IO) {
                // KitePlayer's open() is strict: it is legal only from Idle, Ended and Failed, and
                // a second file loaded while the first is Paused throws. stop() is legal from every
                // state (a no-op when there is nothing to stop). So calling stop() first, always,
                // is how the caller says "replace whatever is playing".
                player.stop()
                player.open(MediaItem(uri = path.uri, openOptions = path.openOptions))
            }
            loggy("KitePlayer: opened, status=${player.state.value.status} duration=${player.state.value.duration}")
        } catch (e: Exception) {
            // PlayerImpl.inject catches this and shows the load-failure OSD. This log line says
            // which uri failed and why, which the OSD cannot.
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
        // A pitch-preserving tempo stage. The engine refuses a live change on an unseekable
        // source, because there is no epoch boundary to switch at. The room's 0.95x slowdown
        // then does not happen, which is the correct outcome for a live stream.
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
        // seekLater is KitePlayer's non-suspending seek: it hands the request to the engine's seek
        // machine and returns, which fits this UiThread member. Precise lands on the exact frame
        // in one step. Do not use the two-phase KeyframeThenRefine: the engine seeks almost at
        // once, so the keyframe and the exact frame arrive a moment apart and the picture flashes
        // twice for every seek. With either mode, KitePlayer's position() reports the target
        // until the seek lands. seekLater throws on a negative position.
        kite?.seekLater(toPositionMs.coerceAtLeast(0L).milliseconds, SeekMode.Precise)
    }

    @UiThread
    override fun currentPositionMs(): Long = kite?.position()?.inWholeMilliseconds ?: 0L

    override suspend fun switchAspectRatio(): String {
        val player = kite ?: return ""
        val next = when (player.state.value.videoScale) {
            VideoScale.Fit -> VideoScale.Fill
            VideoScale.Fill -> VideoScale.Stretch
            VideoScale.Stretch -> VideoScale.Fit
        }
        player.setVideoScale(next)
        return when (next) {
            VideoScale.Fit -> Localization.strings.roomAspectFit
            VideoScale.Fill -> Localization.strings.roomAspectFill
            VideoScale.Stretch -> Localization.strings.roomAspectStretch
        }
    }

    /**
     * The shared subtitle-size control uses the app's own units, with 16 as the default; the
     * engine takes a multiplier over the authored size. Mapping 16 to 1.0 keeps the one slider
     * meaning the same thing on every engine.
     */
    override suspend fun changeSubtitleSize(newSize: Int) {
        kite?.setSubtitleScale((newSize / 16f).coerceAtLeast(0.05f))
    }

    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        // Construct from the composition that owns the output. KitePlayerVideo reports when its
        // renderer is attached, so an early media load waits for the same condition on the
        // native-view path and the pure-Compose path. A change of the setting swaps the
        // presentation over the running player: the engine rebuilds a coupled decoder at the
        // same position by itself.
        LaunchedEffect(Unit) {
            initialize()
        }
        val composedKite by kiteFlow.collectAsState()
        val composeRenderer by KITE_COMPOSE_RENDERER.watchPref()
        // The frosted-glass panels cannot sample the native view, which is why the other engines
        // swap their view type with the same switch. Here glass can read only the Compose path,
        // so glass being on picks that path, and otherwise the preference decides. Desktop
        // overrides both: its native view takes every click meant for the HUD.
        val path = if (kiteEngine.forcesComposeCanvas || composeRenderer || glassEnabled()) {
            KiteRenderPath.ComposeCanvas
        } else {
            KiteRenderPath.NativeView
        }
        val audioVizEnabled by AUDIO_VISUALIZATION.watchPref()
        Box(modifier) {
            // Keep output attached across music/video changes and while the visualizer is disabled.
            KitePlayerVideo(
                player = composedKite,
                modifier = Modifier.fillMaxSize(),
                path = path,
                onRendererAttached = { presented ->
                    if (presented === kiteFlow.value && presentedPlayer.complete(presented)) {
                        onPlayerReady()
                    }
                },
            )
            composedKite?.let { player ->
                val videoDisabled by remember(player) {
                    player.state.map { it.tracks.video.isNotEmpty() && it.tracks.selectedVideo == null }
                        .distinctUntilChanged()
                }.collectAsState(initial = false)
                // A retained renderer frame must not remain visible after the video track is disabled.
                if (videoDisabled) Box(Modifier.fillMaxSize().background(Color.Black))
            }
            if (audioVizEnabled) composedKite?.let { player ->
                val showVisualization by remember(player) {
                    player.state.map { snapshot ->
                        shouldShowAudioVisualization(
                            enabled = true,
                            audioSelected = snapshot.tracks.selectedAudio != null,
                            videoSelected = snapshot.tracks.video.any { !it.isCoverArt && it.id == snapshot.tracks.selectedVideo },
                        )
                    }.distinctUntilChanged()
                }.collectAsState(initial = false)
                // Listen before media opens, so short clips keep their first audio buffers.
                // Turning the preference off detaches the audio tap as well as removing the
                // drawing. The default scan policy reads plain file paths only. Nearly everything
                // here is a URI (a picked file, a link, a YouTube stream), so the scan is opened to
                // any source, and its maps stay in the cache directory across runs.
                val viz = rememberAudioVizState(player, songScan = SONG_SCAN, songMapStore = songMapStore)
                val scope = rememberCoroutineScope()
                LaunchedEffect(viz) {
                    // Zero waits for a musical boundary however long that takes, and a lot of
                    // music has none for minutes. KitePlayer's sample uses the same number.
                    viz.director.maximumHoldSeconds = DIRECTOR_MAX_HOLD_SECONDS
                    // Every display frame on a 120 Hz phone costs battery for no visible gain.
                    viz.framesPerSecond = VISUALIZER_FRAMES_PER_SECOND
                    viz.directed = KITE_AUDIO_VIZ_DIRECTOR.value()
                }
                DisposableEffect(viz) {
                    val controls = KiteVisualizerControls(viz, scope)
                    visualizerFlow.value = controls
                    onDispose { visualizerFlow.compareAndSet(controls, null) }
                }
                if (showVisualization) {
                    KiteAudioViz(viz, Modifier.fillMaxSize())
                }
            }
        }
    }

    /**
     * KitePlayer's gain stops at 1.0: a higher value is refused, not clipped. So this engine keeps
     * the default [gainMax] and adds no gain step to the volume ladder.
     */
    override fun getEngineVolume(): Int =
        ((kite?.state?.value?.volume ?: 1f) * 100).toInt().coerceIn(0, 100)

    override fun setEngineVolume(percent: Int) {
        kite?.setVolume(percent.coerceIn(0, 100) / 100f)
    }

    private companion object {
        /** How long a load waits for the renderer before it fails, instead of holding the mutex. */
        const val RENDERER_ATTACH_TIMEOUT_MS = 15_000L

        /** Max seconds the director holds one drawing with no boundary before a beat changes it. */
        const val DIRECTOR_MAX_HOLD_SECONDS = 30f

        /** The visualizer redraws at most this often, whatever the display's rate. */
        const val VISUALIZER_FRAMES_PER_SECOND = 60

        /** Every source the player can open may be scanned a second time for its song map. */
        val SONG_SCAN = SongScanPolicy(localFiles = true, network = true, customReaders = true)

        /** Finished song maps, kept between runs; a map is about ten kilobytes. */
        val songMapStore: SongMapStore by lazy {
            getCacheDirectoryPath("songmaps")?.let { SongMapStore.inDirectory(it) } ?: SongMapStore.None
        }
    }
}

/**
 * What one engine status means for the room. [playing] is the play state to mirror, or null to
 * leave it as it is. [ownStop] marks a stop that the engine made by itself, so the room is not
 * told about it: a failure is local, shown to the user and never broadcast as a pause.
 */
internal data class KiteStatusMirror(val playing: Boolean?, val buffering: Boolean, val ownStop: Boolean)

/**
 * Buffering counts as playing: the engine is trying to advance, and KitePlayer never pauses by
 * itself on underrun, so a stall must not look like a pause. Opening is the media's lifecycle,
 * not a playback intent, so the play state stays as it is, as with VLCKit's transitional states.
 */
internal fun mirrorOf(status: PlaybackStatus): KiteStatusMirror = when (status) {
    PlaybackStatus.Playing -> KiteStatusMirror(playing = true, buffering = false, ownStop = false)
    PlaybackStatus.Buffering -> KiteStatusMirror(playing = true, buffering = true, ownStop = false)
    PlaybackStatus.Opening -> KiteStatusMirror(playing = null, buffering = true, ownStop = false)
    PlaybackStatus.Paused, PlaybackStatus.Ended, PlaybackStatus.Idle -> KiteStatusMirror(playing = false, buffering = false, ownStop = false)
    PlaybackStatus.Failed -> KiteStatusMirror(playing = false, buffering = false, ownStop = true)
}

/** The tracks card's view of the visualizer: reads are snapshot state, the director switch persists. */
private class KiteVisualizerControls(private val viz: AudioVizState, private val scope: CoroutineScope) : VisualizerControls {
    /**
     * The whole catalogue, which the director also draws from. Held once: the library builds its
     * catalogue at construction and never changes it.
     */
    private val offered: List<Visualization> = viz.catalogue

    override val drawings: List<String> = offered.map { it.name }

    override val showing: Int get() = offered.indexOf(viz.showing)

    override fun show(index: Int) {
        viz.drawing = offered.getOrNull(index) ?: return
    }
    override var directed: Boolean
        get() = viz.directed
        set(value) {
            viz.directed = value
            scope.launch { KITE_AUDIO_VIZ_DIRECTOR.set(value) }
        }
}
