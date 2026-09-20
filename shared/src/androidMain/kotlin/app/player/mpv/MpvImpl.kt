package app.player.mpv

import android.content.Context
import android.os.Build
import androidx.annotation.UiThread
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.i18n.Localization
import app.player.PlayerImpl
import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.player.models.PlayerOptions
import app.player.models.Track
import app.player.mpv.MpvFileUtils.copyAssets
import app.player.mpv.MpvFileUtils.resolveUri
import app.preferences.PrefExtraConfig
import app.preferences.Preferences.MPV_DEBUG_MODE
import app.preferences.Preferences.MPV_EXPORT_CONF
import app.preferences.Preferences.MPV_GPU_NEXT
import app.preferences.Preferences.MPV_HARDWARE_ACCELERATION
import app.preferences.Preferences.MPV_IMPORT_CONF
import app.preferences.Preferences.MPV_INTERPOLATION
import app.preferences.Preferences.MPV_PROFILE
import app.preferences.Preferences.MPV_VIDSYNC
import app.preferences.settings.SettingCategory
import app.preferences.settings.enabledWhen
import app.preferences.settings.withControl
import app.preferences.value
import app.room.RoomViewmodel
import app.uicomponents.glassEnabledNow
import app.utils.ioDispatcher
import app.utils.loggy
import app.utils.playableUri
import app.utils.uri
import io.github.vinceglb.filekit.PlatformFile
import io.github.yuroyami.libmpvkt.EndFileReason
import io.github.yuroyami.libmpvkt.HwdecMode
import io.github.yuroyami.libmpvkt.IdleMode
import io.github.yuroyami.libmpvkt.KeepOpenMode
import io.github.yuroyami.libmpvkt.Mpv
import io.github.yuroyami.libmpvkt.MpvCommands
import io.github.yuroyami.libmpvkt.MpvEvent
import io.github.yuroyami.libmpvkt.MpvProperties
import io.github.yuroyami.libmpvkt.SubAddMode
import io.github.yuroyami.libmpvkt.TrackSelection
import io.github.yuroyami.libmpvkt.VideoOutput
import io.github.yuroyami.libmpvkt.VideoSyncMode
import io.github.yuroyami.libmpvkt.getOrNull
import io.github.yuroyami.libmpvkt.getOrThrow
import io.github.yuroyami.libmpvkt.view.MpvOptions
import io.github.yuroyami.libmpvkt.view.MpvView
import io.github.yuroyami.libmpvkt.view.SurfaceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.Volatile
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import io.github.yuroyami.libmpvkt.TrackType as MpvTrackType

class MpvImpl(vm: RoomViewmodel) : PlayerImpl(vm, MpvEngine) {
    override val supportsVideoTrackSelection = true
    override val supportsChapters: Boolean = true
    override val trackerJobInterval: Duration = 500.milliseconds

    private lateinit var mpvView: MpvView
    private lateinit var ctx: Context

    /** The running core. Every file starts a new one, so this changes with each load. */
    @Volatile
    private var core: Mpv? = null

    /** Collects [core]'s events and properties. Cancelled before that core closes. */
    private var coreJob: Job? = null

    /** The last `time-pos` mpv reported, in ms. */
    @Volatile
    private var mpvPos = 0L

    /** The last `seekable` mpv reported. A core that has not spoken yet counts as seekable. */
    @Volatile
    private var mpvSeekable = true

    /** The last `volume` mpv reported, as a whole percent on mpv's own 0 to [gainMax] ladder. */
    @Volatile
    private var mpvVolume = 100

    /**
     * Every call into the core runs here, one at a time.
     *
     * mpv answers a property read on its own thread, so a call made while that thread is busy
     * (a slow decode, a stalled stream) waits for it. On the UI thread that wait is a frozen
     * picture, and past five seconds Android kills the app for not answering a key press. One
     * thread of our own also keeps calls in the order they were made, which the UI thread did.
     */
    private val coreCalls = ioDispatcher.limitedParallelism(1)

    private var durationWaitJob: Job? = null

    override fun initialize() {
        ctx = mpvView.context.applicationContext
        copyAssets(ctx)
        startCore()
        isInitialized = true
        startTrackingProgress()
    }

    override suspend fun destroy() {
        if (!isInitialized) return
        // The guards go first: the position tracker polls every 500 ms, and a call that arrives after
        // the core is gone must find no core. A core that closes under a call throws, and withCore
        // absorbs that.
        isInitialized = false
        playerSupervisorJob.cancel()

        withContext(Dispatchers.Main) {
            releaseCore()
            mpvView.destroy()
        }
    }

    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                // A recreated view takes over from the old one, and the old view's core closes with it.
                if (::mpvView.isInitialized) {
                    releaseCore()
                    mpvView.destroy()
                }
                mpvView = MpvView(context)
                initialize()
                onPlayerReady()
                mpvView
            },
        )
    }

    override suspend fun configurableSettings() = SettingCategory(
        key = "engine-mpv",
        title = { it.uisettingCategMpv },
        icon = Icons.Filled.SettingsInputComponent
    ) {
        +MPV_HARDWARE_ACCELERATION.withControl(PrefExtraConfig.BooleanCallback { b ->
            onCore { it[MpvProperties.Hwdec] = if (b) HwdecMode.Auto else HwdecMode.No }
        })
        +MPV_GPU_NEXT.withControl(PrefExtraConfig.BooleanCallback { b ->
            // A core without a surface must keep vo=null. The next core starts with the new choice.
            onCore { if (it.attachedSurface != null) it[MpvProperties.Vo] = if (b) VideoOutput.GpuNext else VideoOutput.Gpu }
        })
        +MPV_VIDSYNC.withControl(PrefExtraConfig.MultiChoice(
            entries = { vidsyncEntries.associateWith { it } },
            onItemChosen = { videoSync ->
                VideoSyncMode.entries.firstOrNull { it.mpvName == videoSync }
                    ?.let { mode -> onCore { it[MpvProperties.VideoSync] = mode } }
            }
        ))
        +MPV_INTERPOLATION.withControl(PrefExtraConfig.BooleanCallback { b ->
            onCore { it[MpvProperties.Interpolation] = b }
        }).enabledWhen {
            val currentVidSyncMode = MPV_VIDSYNC.value()
            currentVidSyncMode != "audio" && currentVidSyncMode != "desync"
        }
        +MPV_PROFILE.withControl(PrefExtraConfig.MultiChoice(
            entries = { profileEntries.associateWith { it } },
            onItemChosen = { profile -> onCore { it.command(MpvCommands.applyProfile(profile)) } }
        ))
        +MPV_DEBUG_MODE.withControl(PrefExtraConfig.Slider(maxValue = 3, minValue = 0) { itemChosen ->
            onCore { it.command(MpvCommands.scriptBinding("stats/display-page-$itemChosen")) }
        })
        // mpv.conf import/export, attached to the engine category so it only shows with mpv.
        +MPV_IMPORT_CONF
        +MPV_EXPORT_CONF
    }

    override suspend fun hasMedia(): Boolean {
        if (!isInitialized) return false
        return withContext(coreCalls) {
            (withCore { it[MpvProperties.PlaylistCount].getOrNull() } ?: 0L) > 0L
        }
    }

    override suspend fun isPlaying(): Boolean {
        if (!isInitialized) return false
        return withContext(coreCalls) {
            withCore { it[MpvProperties.Pause].getOrNull() != true } ?: false
        }
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return
        withContext(coreCalls) {
            playerManager.media.value?.tracks?.clear()

            val tracks = withCore { it[MpvProperties.TrackList].getOrNull() } ?: return@withContext
            for (track in tracks) {
                val type = when (track.type) {
                    MpvTrackType.Audio -> TrackType.AUDIO
                    MpvTrackType.Sub -> TrackType.SUBTITLE
                    MpvTrackType.Video -> if (track.isAlbumArt) continue else TrackType.VIDEO
                }

                val trackName = track.title?.takeIf { it.isNotBlank() } ?: track.lang ?: track.codec ?: ""

                playerManager.media.value?.tracks?.add(
                    MpvTrack(
                        name = trackName,
                        type = type,
                        index = track.id,
                        selected = track.selected,
                        language = track.lang,
                        channelCount = track.demuxChannelCount,
                        channelLayout = track.demuxChannels,
                        codec = track.codec,
                        videoDescription = track.demuxHeight?.let { "${it}p" },
                    )
                )
            }
        }
    }

    override suspend fun selectTrack(track: Track?, type: TrackType) {
        if (!isInitialized) return
        withContext(coreCalls) {
            val selection = track?.let { TrackSelection.Id(it.index) } ?: TrackSelection.No
            when (type) {
                TrackType.SUBTITLE -> withCore { it[MpvProperties.Sid] = selection }
                TrackType.AUDIO -> withCore { it[MpvProperties.Aid] = selection }
                TrackType.VIDEO -> withCore { it[MpvProperties.Vid] = selection }
            }
            playerManager.currentTrackChoices.remember(type, track)
        }
    }

    override suspend fun analyzeChapters(mediafile: MediaFile) {
        if (!isInitialized) return
        mediafile.chapters.clear()

        withContext(coreCalls) {
            val chapters = withCore { it[MpvProperties.ChapterList].getOrNull() } ?: return@withContext
            chapters.forEachIndexed { i, chapter ->
                mediafile.chapters.add(
                    Chapter(
                        index = i,
                        name = chapter.title ?: "Chapter $i",
                        timeOffsetMillis = (chapter.timeSeconds * 1000).roundToLong()
                    )
                )
            }
        }
    }

    override suspend fun jumpToChapter(chapter: Chapter) {
        if (!isInitialized) return
        super.jumpToChapter(chapter)

        withContext(coreCalls) {
            withCore { it[MpvProperties.Chapter] = chapter.index.toLong() }
        }
    }

    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return
        withContext(coreCalls) { reapplyIndexedTrackChoices() }
    }

    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
        if (!isInitialized) return
        withContext(coreCalls) {
            // playableUri gives a file:// uri for our own downloaded subs (a bare path has no
            // scheme, so resolveUri's `when(scheme)` fell through to null) and the content:// uri
            // for picker results.
            ctx.resolveUri(uri.playableUri)?.let { subUri ->
                // A file mpv refuses throws here, so the caller reports a failure, not a success.
                withCore { it.command(MpvCommands.subAdd(subUri, SubAddMode.Cached)).getOrThrow() }
            }
        }
    }

    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
        installMpvSubfontIfNeeded()
        ctx.resolveUri(location.file.uri)?.let { playOnFreshCore(it) }
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        installMpvSubfontIfNeeded()
        playOnFreshCore(location.url)
    }

    override suspend fun pause() {
        if (!isInitialized) return
        withContext(coreCalls) { withCore { it[MpvProperties.Pause] = true } }
    }

    override suspend fun play() {
        if (!isInitialized) return
        withContext(coreCalls) { withCore { it[MpvProperties.Pause] = false } }
    }

    override suspend fun setSpeed(speed: Double) {
        if (!isInitialized) return
        withContext(coreCalls) { withCore { it[MpvProperties.Speed] = speed } }
    }

    /**
     * The observed value, not a read: the tracker asks twice a second, and mpv only says "not
     * seekable" for a live stream, which is a fact about the file rather than the moment.
     */
    override suspend fun isSeekable(): Boolean = isInitialized && mpvSeekable

    @UiThread
    override fun seekTo(toPositionMs: Long) {
        if (!isInitialized) return
        super.seekTo(toPositionMs)
        /* Where mpv is about to be. A paused core reports no new time-pos for a while, and until
         * it does every reader here would answer with the position before the jump: two jumps in
         * a row then both counted from the same place. */
        mpvPos = toPositionMs
        // time-pos is a double, so seeks and chapter jumps keep their sub-second precision.
        onCore { it[MpvProperties.TimePos] = toPositionMs / 1000.0 }
    }

    /** mpv's own `time-pos` as it last reported it. [seekTo] samples the target, so a seek shows at once. */
    override fun currentPositionMs(): Long = if (isInitialized) mpvPos else 0L

    override suspend fun switchAspectRatio(): String {
        if (!isInitialized) return ""
        return withContext(coreCalls) {
            withCore { mpv ->
                // mpv prints this option with %f, so its values compare as the strings below.
                val currentAspect = mpv.getString("video-aspect-override")
                val currentPanscan = mpv[MpvProperties.Panscan].getOrNull()

                // mpv value to the spoken label; the last entry is pan-and-scan rather than a ratio.
                val aspectRatios = listOf(
                    "-1.000000" to Localization.strings.roomAspectOriginal,
                    "1.777778" to Localization.strings.roomAspectRatioLabel("16:9"),
                    "1.600000" to Localization.strings.roomAspectRatioLabel("16:10"),
                    "1.333333" to Localization.strings.roomAspectRatioLabel("4:3"),
                    "2.350000" to Localization.strings.roomAspectRatioLabel("2.35:1"),
                    "panscan" to Localization.strings.roomAspectPanscan,
                )

                var enablePanscan = false
                val nextAspect = if (currentPanscan == 1.0) {
                    aspectRatios[0]
                } else if (currentAspect == "2.350000") {
                    enablePanscan = true
                    aspectRatios[5]
                } else {
                    // An unknown current value (a user config) restarts the cycle at the first ratio.
                    aspectRatios.getOrElse(aspectRatios.indexOfFirst { it.first == currentAspect } + 1) { aspectRatios[1] }
                }

                if (enablePanscan) {
                    mpv.setString("video-aspect-override", "-1")
                    mpv[MpvProperties.Panscan] = 1.0
                } else {
                    mpv.setString("video-aspect-override", nextAspect.first)
                    mpv[MpvProperties.Panscan] = 0.0
                }

                nextAspect.second
            } ?: ""
        }
    }

    override suspend fun changeSubtitleSize(newSize: Int) {
        if (!isInitialized) return
        withContext(coreCalls) {
            val s: Double = when {
                newSize == 16 -> 1.0
                newSize > 16 -> 1.0 + (newSize - 16) * 0.05
                else -> 1.0 - (16 - newSize) * (1.0 / 16)
            }

            withCore { it[MpvProperties.SubScale] = s }
        }
    }

    /** Every file starts on a fresh core, as it always has here. The view hands it the surface. */
    @UiThread
    private fun playOnFreshCore(pathOrUrl: String) {
        startCore()
        mpvView.playFile(pathOrUrl).getOrThrow()
    }

    /**
     * Starts a new core on [mpvView]. The view hands the old core's surface to the new one and closes
     * the old core in the background, after its flows stop here.
     */
    @UiThread
    private fun startCore() {
        releaseCore()
        mpvView.initialize(startOptions())
        val mpv = mpvView.mpv ?: return
        // Set after the start, as they always were, so a user's mpv.conf cannot override them.
        val playerOptions = PlayerOptions.get()
        mpv[MpvProperties.SavePositionOnQuit] = false
        mpv[MpvProperties.Idle] = IdleMode.Once
        mpv[MpvProperties.Alang] = languages(playerOptions.audioPreference)
        mpv[MpvProperties.Slang] = languages(playerOptions.ccPreference)
        mpv[MpvProperties.Pause] = true
        // The gain rung: mpv clamps volume at 130 by default.
        mpv[MpvProperties.VolumeMax] = gainMax.toDouble()
        core = mpv
        watch(mpv)
    }

    /** How each core starts, from the mpv settings. Everything else keeps libmpvKt's phone defaults. */
    private fun startOptions() = MpvOptions(
        configDir = ctx.filesDir,
        cacheDir = ctx.cacheDir,
        // TextureView keeps the picture in the view tree, so Haze can blur it under glass.
        // SurfaceView can use a hardware overlay (less power, no GPU copy) but no in-app effect sees it.
        surfaceType = if (glassEnabledNow()) SurfaceType.Texture else SurfaceType.Surface,
        vo = if (MPV_GPU_NEXT.value()) VideoOutput.GpuNext else VideoOutput.Gpu,
        hwdec = if (MPV_HARDWARE_ACCELERATION.value()) HwdecMode.Auto else HwdecMode.No,
        profile = MPV_PROFILE.value(),
        videoSync = VideoSyncMode.entries.firstOrNull { it.mpvName == MPV_VIDSYNC.value() } ?: VideoSyncMode.Audio,
        interpolation = MPV_INTERPOLATION.value(),
        tlsCaFile = File(ctx.filesDir, "cacert.pem"),
        demuxerMaxBytes = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64L else 32L) * 1024 * 1024,
        inputDefaultBindings = true,
        // The room hears that a file ended through EndFile, which keep-open would hold back.
        keepOpen = KeepOpenMode.No,
    )

    /** A language preference as mpv's list: "eng,jpn" is two entries, and an empty one is none. */
    private fun languages(preference: String) = preference.split(',').map(String::trim).filter(String::isNotEmpty)

    /**
     * Forgets the running core and stops its flows, so none of its last events reach the next core.
     * The view closes that core on a thread of its own, so it is paused first to stay silent until then.
     */
    private fun releaseCore() {
        coreJob?.cancel()
        coreJob = null
        core?.let { old -> runCatching { old[MpvProperties.Pause] = true } }
        core = null
        mpvPos = 0L
        mpvSeekable = true
    }

    /** Follows [mpv]'s events and the four properties the room shows, until [releaseCore]. */
    private fun watch(mpv: Mpv) {
        val job = SupervisorJob(playerSupervisorJob)
        coreJob = job
        val scope = CoroutineScope(Dispatchers.Default + job)
        // Events have no replay. An undispatched start subscribes before the caller's loadfile goes out.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mpv.events.collect { event ->
                when (event) {
                    is MpvEvent.StartFile -> if (!viewmodel.isSoloMode) announceWhenDurationKnown(scope)
                    is MpvEvent.EndFile -> scope.launch(Dispatchers.Main) { onFileEnded(event.reason) }
                    else -> Unit
                }
            }
        }
        scope.follow(mpv, mpv.observe(MpvProperties.TimePos)) { if (it != null) mpvPos = (it * 1000).toLong() }
        // Read on the UI thread, so both are followed rather than asked for: see [coreCalls].
        scope.follow(mpv, mpv.observe(MpvProperties.Seekable)) { if (it != null) mpvSeekable = it }
        scope.follow(mpv, mpv.observe(MpvProperties.Volume)) { if (it != null) mpvVolume = it.toInt() }
        scope.follow(mpv, mpv.observe(MpvProperties.Duration)) {
            if (it != null) playerManager.timeFullMillis.value = (it * 1000).toLong()
        }
        // Just to inform the UI.
        scope.follow(mpv, mpv.observe(MpvProperties.Pause)) { if (it != null) playerManager.isNowPlaying.value = !it }
        // mpv stalling on its cache, which the room shows as a waiting indicator and never treats as a pause.
        scope.follow(mpv, mpv.observe(MpvProperties.PausedForCache)) {
            if (it != null) playerManager.isBuffering.value = it
        }
    }

    /** Collects [flow] in this scope. A flow that fails ends there, with a log line unless its core closed. */
    private fun <T> CoroutineScope.follow(mpv: Mpv, flow: Flow<T>, action: (T) -> Unit) = launch {
        flow.catch { if (!mpv.isClosed) loggy("mpv: an observed property failed: ${it.message}") }
            .collect { action(it) }
    }

    /**
     * Announces the file once mpv knows its duration. One wait per file: the next core cancels it,
     * so a fast second load cannot announce the new file with the old one's timing.
     */
    private fun announceWhenDurationKnown(scope: CoroutineScope) {
        durationWaitJob?.cancel()
        durationWaitJob = scope.launch {
            // timeFullMillis is wiped to 0 on every inject (PlayerImpl.installMedia), so this really
            // waits for THIS file's duration; a stale one used to announce the old name, size and
            // duration. Bounded: a file with no duration (a live stream) still announces, with 0.
            var waitedMs = 0L
            while (isActive && playerManager.timeFullMillis.value <= 0 && waitedMs < 5000) {
                delay(50)
                waitedMs += 50
            }
            if (!isActive) return@launch
            playerManager.media.value?.fileDuration = playerManager.timeFullMillis.value.toDouble().div(1000.0)
            announceFileLoaded()
        }
    }

    /**
     * Only a real end of the file reaches the room. mpv also reports Eof when a stream drops early, so
     * the position has to agree. Anything else (a decode error, a stop) ends locally, and the room is
     * told nothing.
     */
    private suspend fun onFileEnded(reason: EndFileReason) {
        val dur = playerManager.timeFullMillis.value
        val pos = playerManager.timeCurrentMillis.value
        val atEnd = reason == EndFileReason.Eof && dur > 0L && pos >= dur - 1500L
        if (!atEnd) viewmodel.protocol.noteExpectedPlaybackState(paused = true)
        pause()
        if (atEnd) onPlaybackEnded()
    }

    /**
     * Runs [block] on the running core, or does nothing when there is none. A core that closes during
     * the call throws, and a late call has nothing left to do, so that reads as no core too.
     */
    private inline fun <T> withCore(block: (Mpv) -> T): T? {
        val mpv = core ?: return null
        return try {
            block(mpv)
        } catch (e: IllegalStateException) {
            if (mpv.isClosed) null else throw e
        }
    }

    /** Sends [block] to the core and returns at once, for a caller that cannot wait (the UI thread). */
    private fun onCore(block: (Mpv) -> Unit) {
        playerScopeIO.launch(coreCalls) { withCore(block) }
    }

    /* mpv's own volume property is the whole ladder: 0 to 100 is its output, 100 to 200 is
     * amplification once volume-max has been raised at start. */
    override fun getEngineVolume(): Int = mpvVolume.coerceIn(0, 100)
    override fun setEngineVolume(percent: Int) {
        setVolume(percent.coerceIn(0, 100))
    }

    override val gainMax: Int = 200
    override fun getGain(): Int = mpvVolume.coerceIn(100, gainMax)
    override fun setGain(percent: Int) {
        setVolume(percent.coerceIn(100, gainMax))
    }

    /** The slider moves now; mpv's own value follows and comes back through the observed property. */
    private fun setVolume(percent: Int) {
        mpvVolume = percent
        onCore { it[MpvProperties.Volume] = percent.toDouble() }
    }

    private companion object {
        /** The video-sync modes the setting offers, in mpv's order. */
        val vidsyncEntries = VideoSyncMode.entries.map { it.mpvName }

        val profileEntries = listOf("fast", "high-quality", "gpu-hq", "low-latency", "sw-fast")
    }
}
