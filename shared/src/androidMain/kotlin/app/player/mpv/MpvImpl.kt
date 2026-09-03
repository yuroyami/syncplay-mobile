package app.player.mpv

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.view.LayoutInflater
import androidx.annotation.UiThread
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C.STREAM_TYPE_MUSIC
import app.R
import app.player.PlayerImpl
import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
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
import app.utils.playableUri
import app.utils.uri
import io.github.vinceglb.filekit.PlatformFile
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_aspect_original
import syncplaymobile.shared.generated.resources.room_aspect_panscan
import syncplaymobile.shared.generated.resources.room_aspect_ratio_label
import syncplaymobile.shared.generated.resources.uisetting_categ_mpv
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class MpvImpl(vm: RoomViewmodel) : PlayerImpl(vm, MpvEngine) {
    lateinit var audioManager: AudioManager
    var mpvPos = 0L
    private lateinit var observer: MPVLib.EventObserver
    private var durationWaitJob: kotlinx.coroutines.Job? = null
    lateinit var mpvView: MPVView
    private lateinit var ctx: Context
    override val supportsChapters: Boolean = true
    override val trackerJobInterval: Duration = 500.milliseconds

    override fun initialize() {
        ctx = mpvView.context.applicationContext
        audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        copyAssets(ctx)

        // A recreated view means a new surface for a core that already exists. libmpv's handle
        // is process-global, so the only clean way to rehost it is the same destroy-then-create
        // every file load already does; a second create over a live core aborts.
        if (isInitialized) {
            removeObserver()
            MPVLib.destroy()
        }
        mpvView.initialize(ctx.filesDir.path, ctx.cacheDir.path)
        // The gain rung: mpv clamps volume at 130 by default.
        runCatching { MPVLib.setPropertyInt("volume-max", gainMax) }
        isInitialized = true
        mpvObserverAttach()
    }

    override suspend fun destroy() {
        if (!isInitialized) return
        // Flip the guards and stop the position tracker BEFORE tearing libmpv down. MPVLib is a
        // process-global static handle (g_mpv); once mpvView.destroy() nulls it, any lingering
        // tracker call (isSeekable()/currentPositionMs(), polled every 500ms) would sail past its
        // `if (!isInitialized)` guard and trip the native CHECK_MPV_INIT(), aborting with "libmpv is
        // not initialized". Setting isInitialized=false makes per-method guards bail; cancelling the
        // supervisor job stops the tracker's next tick. mpv is the one engine that hard-crashes here
        // because its calls go through a global handle, not a nullable per-instance player.
        isInitialized = false
        playerSupervisorJob.cancel()

        withContext(Dispatchers.Main) {
            // Detach the observer first: MPVLib.observers is a process-global static list, so an
            // un-removed observer keeps this MpvImpl (and its RoomViewmodel graph) reachable past
            // teardown until the next attach replaces it.
            removeObserver()
            mpvView.destroy()
        }
    }

    @SuppressLint("InflateParams")
    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                mpvView = LayoutInflater.from(context).inflate(R.layout.mpvview, null) as MPVView
                initialize()
                onPlayerReady()
                return@AndroidView mpvView
            },
            update = {

            }
        )
    }

    override suspend fun configurableSettings() = SettingCategory(
        title = Res.string.uisetting_categ_mpv,
        icon = Icons.Filled.SettingsInputComponent
    ) {
        +MPV_HARDWARE_ACCELERATION.withControl(PrefExtraConfig.BooleanCallback { b ->
            MPVLib.setOptionString("hwdec", if (b) "auto" else "no")
        })
        +MPV_GPU_NEXT.withControl(PrefExtraConfig.BooleanCallback { b ->
            MPVLib.setOptionString("vo", if (b) "gpu-next" else "gpu")
        })
        +MPV_VIDSYNC.withControl(PrefExtraConfig.MultiChoice(
            entries = { MPVView.vidsyncEntries.zip(MPVView.vidsyncEntries).toMap() },
            onItemChosen = { videoSync -> MPVLib.setOptionString("video-sync", videoSync) }
        ))
        +MPV_INTERPOLATION.withControl(PrefExtraConfig.BooleanCallback { b ->
            MPVLib.setOptionString("interpolation", if (b) "yes" else "no")
        }).enabledWhen {
            val currentVidSyncMode = MPV_VIDSYNC.value()
            currentVidSyncMode != "audio" && currentVidSyncMode != "desync"
        }
        +MPV_PROFILE.withControl(PrefExtraConfig.MultiChoice(
            entries = { MPVView.profileEntries.zip(MPVView.profileEntries).toMap() },
            onItemChosen = { profile -> MPVLib.setOptionString("profile", profile) }
        ))
        +MPV_DEBUG_MODE.withControl(PrefExtraConfig.Slider(maxValue = 3, minValue = 0) { itemChosen ->
            MPVLib.command(arrayOf("script-binding", "stats/display-page-$itemChosen"))
        })
        // mpv.conf import/export, attached to the engine category so it only shows with mpv.
        +MPV_IMPORT_CONF
        +MPV_EXPORT_CONF
    }

    override suspend fun hasMedia(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) {
            val c = MPVLib.getPropertyInt("playlist-count")
            c != null && c > 0
        }
    }

    override suspend fun isPlaying(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) {
            !mpvView.paused
        }
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            playerManager.media.value?.tracks?.clear()

            // Because events are async, properties can disappear at any moment, so prefer
            // null-safe reads (?: return/continue) over !! which would crash mid-analysis.
            val count = MPVLib.getPropertyInt("track-list/count") ?: return@withContext
            for (i in 0 until count) {
                val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
                if (type != "audio" && type != "sub") continue
                val mpvId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
                val lang = MPVLib.getPropertyString("track-list/$i/lang")
                val title = MPVLib.getPropertyString("track-list/$i/title")
                val selected = MPVLib.getPropertyBoolean("track-list/$i/selected") ?: false

                /** Speculating the track name based on whatever info there is on it */
                val trackName = when {
                    !title.isNullOrEmpty() && !lang.isNullOrEmpty() -> "$title [$lang]"
                    !title.isNullOrEmpty() -> "$title [UND]"
                    !lang.isNullOrEmpty() -> "Track [$lang]"
                    else -> "Track $mpvId [UND]"
                }

                playerManager.media.value?.tracks?.add(
                    MpvTrack(
                        name = trackName,
                        type = if (type == "audio") TrackType.AUDIO else TrackType.SUBTITLE,
                        index = mpvId,
                        selected = selected
                    )
                )
            }
        }
    }

    override suspend fun selectTrack(track: Track?, type: TrackType) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            when (type) {
                TrackType.SUBTITLE -> {
                    if (track != null) {
                        MPVLib.setPropertyInt("sid", track.index)
                    } else {
                        MPVLib.setPropertyString("sid", "no")
                    }

                    playerManager.currentTrackChoices.subtitleSelectionIndexMpv = track?.index ?: -1
                }

                TrackType.AUDIO -> {
                    if (track != null) {
                        MPVLib.setPropertyInt("aid", track.index)
                    } else {
                        MPVLib.setPropertyString("aid", "no")
                    }

                    playerManager.currentTrackChoices.audioSelectionIndexMpv = track?.index ?: -1
                }
            }
        }
    }

    override suspend fun analyzeChapters(mediafile: MediaFile) {
        if (!isInitialized) return
        mediafile.chapters.clear()

        withContext(Dispatchers.Main.immediate) {
            val count = MPVLib.getPropertyInt("chapter-list/count") ?: return@withContext

            for (i in 0 until count) {
                val title = MPVLib.getPropertyString("chapter-list/$i/title")
                val time = MPVLib.getPropertyDouble("chapter-list/$i/time") ?: continue

                mediafile.chapters.add(
                    Chapter(
                        index = i,
                        name = title ?: "Chapter $i",
                        timeOffsetMillis = (time * 1000).roundToLong()
                    )
                )
            }
        }
    }

    override suspend fun jumpToChapter(chapter: Chapter) {
        if (!isInitialized) return
        super.jumpToChapter(chapter)

        withContext(Dispatchers.Main.immediate) {
            MPVLib.setPropertyInt("chapter", chapter.index)
        }
    }

    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            val subIndex = playerManager.currentTrackChoices.subtitleSelectionIndexMpv
            val audioIndex = playerManager.currentTrackChoices.audioSelectionIndexMpv


            val ccMap = playerManager.media.value?.tracks?.filter { it.type == TrackType.SUBTITLE }
            val audioMap = playerManager.media.value?.tracks?.filter { it.type == TrackType.AUDIO }

            val ccGet = ccMap?.firstOrNull { it.index == subIndex }
            val audioGet = audioMap?.firstOrNull { it.index == audioIndex }

            with(playerManager.player) {
                if (subIndex == -1) {
                    selectTrack(null, TrackType.SUBTITLE)
                } else if (ccGet != null) {
                    selectTrack(ccGet, TrackType.SUBTITLE)
                }

                if (audioIndex == -1) {
                    selectTrack(null, TrackType.AUDIO)
                } else if (audioGet != null) {
                    selectTrack(audioGet, TrackType.AUDIO)
                }
            }
        }
    }

    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
        if (!isInitialized) return
        withContext(Dispatchers.Main) {
            // playableUri gives a file:// uri for our own downloaded subs (a bare path has no
            // scheme, so resolveUri's `when(scheme)` fell through to null) and the content:// uri
            // for picker results.
            ctx.resolveUri(uri.playableUri)?.let { subUri ->
                MPVLib.command(arrayOf("sub-add", subUri, "cached"))
            }
        }
    }

    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
        installMpvSubfontIfNeeded()
        ctx.resolveUri(location.file.uri)?.let {
            if (isInitialized) MPVLib.destroy()
            mpvView.initialize(ctx.filesDir.path, ctx.cacheDir.path)
            mpvObserverAttach()
            mpvView.playFile(it)
        }
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        installMpvSubfontIfNeeded()
        if (isInitialized) MPVLib.destroy()
        mpvView.initialize(ctx.filesDir.path, ctx.cacheDir.path)
        mpvObserverAttach()
        mpvView.playFile(location.url)
    }

    override suspend fun pause() {
        if (!isInitialized) return
        mpvView.paused = true
    }

    override suspend fun play() {
        if (!isInitialized) return
        mpvView.paused = false
    }

    override suspend fun setSpeed(speed: Double) {
        if (!isInitialized) return
        MPVLib.setPropertyDouble("speed", speed)
    }

    override suspend fun isSeekable(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) {
            // Only report non-seekable when mpv explicitly says so (e.g. live streams); default to
            // seekable if the property isn't available yet so the position tracker keeps polling.
            MPVLib.getPropertyBoolean("seekable") ?: true
        }
    }

    @UiThread
    override fun seekTo(toPositionMs: Long) {
        if (!isInitialized) return
        super.seekTo(toPositionMs)
        // Seek with sub-second precision via the double property. mpvView.timePos is INT-backed
        // (whole seconds), which would snap seeks/chapter-jumps to a second boundary and disagree
        // with the fractional position currentPositionMs() reports.
        MPVLib.setPropertyDouble("time-pos", toPositionMs.toDouble() / 1000.0)
    }

    override fun currentPositionMs(): Long {
        if (!isInitialized) return 0L
        // The observed `time-pos` (mpvPos) arrives as INT64, quantized to whole seconds, because
        // mpv's JNI doesn't push double-format property updates. Read the precise fractional value
        // directly so position reports aren't a 1-second sawtooth that nudges the sync layer into
        // corrective micro-seeks. Fall back to mpvPos if unavailable.
        val precise = MPVLib.getPropertyDouble("time-pos")
        return if (precise != null) (precise * 1000.0).toLong() else mpvPos
    }

    override suspend fun switchAspectRatio(): String {
        if (!isInitialized) return ""
        return withContext(Dispatchers.Main.immediate) {
            val currentAspect = MPVLib.getPropertyString("video-aspect-override")
            val currentPanscan = MPVLib.getPropertyDouble("panscan")

            // mpv value to the spoken label; the last entry is pan-and-scan rather than a ratio.
            val aspectRatios = listOf(
                "-1.000000" to getString(Res.string.room_aspect_original),
                "1.777778" to getString(Res.string.room_aspect_ratio_label, "16:9"),
                "1.600000" to getString(Res.string.room_aspect_ratio_label, "16:10"),
                "1.333333" to getString(Res.string.room_aspect_ratio_label, "4:3"),
                "2.350000" to getString(Res.string.room_aspect_ratio_label, "2.35:1"),
                "panscan" to getString(Res.string.room_aspect_panscan),
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
                MPVLib.setPropertyString("video-aspect-override", "-1")
                MPVLib.setPropertyDouble("panscan", 1.0)
            } else {
                MPVLib.setPropertyString("video-aspect-override", nextAspect.first)
                MPVLib.setPropertyDouble("panscan", 0.0)
            }

            return@withContext nextAspect.second
        }
    }

    override suspend fun changeSubtitleSize(newSize: Int) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            val s: Double = when {
                newSize == 16 -> 1.0
                newSize > 16 -> 1.0 + (newSize - 16) * 0.05
                else -> 1.0 - (16 - newSize) * (1.0 / 16)
            }

            MPVLib.setPropertyDouble("sub-scale", s)
        }
    }

    private fun mpvObserverAttach() {
        removeObserver()

        observer = object : MPVLib.EventObserver {
            override fun eventProperty(property: String) {}

            override fun eventProperty(property: String, value: Long) {
                when (property) {
                    "time-pos" -> mpvPos = value * 1000
                    "duration" -> playerManager.timeFullMillis.value = value * 1000
                }
            }

            override fun eventProperty(property: String, value: Boolean) {
                when (property) {
                    "pause" -> {
                        playerManager.isNowPlaying.value = !value //Just to inform UI
                    }
                }
            }

            override fun eventProperty(property: String, value: String) {}
            override fun eventProperty(property: String, value: Double) {}

            override fun event(eventId: Int) {
                when (eventId) {
                    MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
                        if (viewmodel.isSoloMode) return
                        // One wait per file: a fast second load cancels the first file's waiter,
                        // which would otherwise announce the new file with the old one's timing.
                        durationWaitJob?.cancel()
                        durationWaitJob = playerScopeIO.launch {
                            // timeFullMillis is wiped to 0 on every inject (PlayerImpl.installMedia),
                            // so this genuinely waits for THIS file's duration event. Before that
                            // wipe existed, the previous file's stale duration made the wait exit
                            // instantly on 2nd+ injections and the room got announced stale
                            // metadata (old name/size/duration). Bounded wait: files with no
                            // detectable duration (live streams) still announce, with 0.
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

                    MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                        playerScopeMain.launch {
                            // The event carries no reason through the JNI, so the position says
                            // whether this was the end of the file. Anything else (a decode
                            // error, a stop) ends locally: the room is told nothing.
                            val dur = playerManager.timeFullMillis.value
                            val pos = playerManager.timeCurrentMillis.value
                            val atEnd = dur > 0L && pos >= dur - 1500L
                            if (!atEnd) viewmodel.protocol.noteExpectedPlaybackState(paused = true)
                            pause()
                            if (atEnd) onPlaybackEnded()
                        }
                    }
                }
            }
        }

        mpvView.addObserver(observer)

        startTrackingProgress()
    }

    fun removeObserver() {
        if (::observer.isInitialized) {
            mpvView.removeObserver(observer)
        }
    }

    /* mpv's own volume property is the whole ladder: 0 to 100 is its output, 100 to 200 is
     * amplification once volume-max has been raised at init. */
    override fun getEngineVolume(): Int = (MPVLib.getPropertyInt("volume") ?: 100).coerceIn(0, 100)
    override fun setEngineVolume(percent: Int) {
        MPVLib.setPropertyInt("volume", percent.coerceIn(0, 100))
    }

    override val gainMax: Int = 200
    override fun getGain(): Int = (MPVLib.getPropertyInt("volume") ?: 100).coerceIn(100, gainMax)
    override fun setGain(percent: Int) {
        MPVLib.setPropertyInt("volume", percent.coerceIn(100, gainMax))
    }
}
