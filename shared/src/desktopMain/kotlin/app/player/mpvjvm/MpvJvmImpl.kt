package app.player.mpvjvm

import androidx.annotation.UiThread
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.player.DesktopAspectMode
import app.player.DesktopFramePipeline
import app.player.FrameCanvas
import app.player.PlayerImpl
import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.player.models.Track
import app.player.mpv.installMpvSubfontIfNeeded
import app.preferences.Pref
import app.preferences.PrefExtraConfig
import app.preferences.Preferences.MPV_EXPORT_CONF
import app.preferences.Preferences.MPV_HARDWARE_ACCELERATION
import app.preferences.Preferences.MPV_IMPORT_CONF
import app.preferences.settings.SettingCategory
import app.preferences.value
import app.room.RoomViewmodel
import app.utils.desktopAppDataDir
import app.utils.generateTimestampMillis
import app.utils.loggy
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.uisetting_audio_delay_summary
import syncplaymobile.shared.generated.resources.uisetting_audio_delay_title
import syncplaymobile.shared.generated.resources.uisetting_categ_mpv
import syncplaymobile.shared.generated.resources.uisetting_subtitle_delay_summary
import syncplaymobile.shared.generated.resources.uisetting_subtitle_delay_title
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Desktop libmpv engine over the [LibMpv] JNA binding.
 *
 * Video: mpv's SOFTWARE render API — mpv writes BGRA frames into a native buffer on our render
 * thread, which hands them to the shared [DesktopFramePipeline]/[FrameCanvas]. Aspect changes go
 * through mpv itself (video-aspect-override/panscan, like Android), so the canvas always FITs.
 *
 * Threading: one daemon event-loop thread (mpv_wait_event), one daemon render thread woken by
 * mpv's update callback (rendering inside the callback is forbidden by the API). Engine
 * construction runs off the EDT like VlcjImpl. Property reads are plain C calls, safe anywhere.
 */
class MpvJvmImpl(vm: RoomViewmodel) : PlayerImpl(vm, MpvJvmEngine) {

    private val mpv: LibMpv = MpvNativeLoader.library
        ?: throw IllegalStateException("MpvJvmImpl created while libmpv is unavailable")

    private var handle: Pointer? = null
    private var renderCtx: Pointer? = null

    @Volatile private var mediaLoaded = false
    @Volatile private var running = false

    override val supportsChapters: Boolean = true
    override val supportsPictureInPicture: Boolean = false
    override val supportsScreenshot: Boolean = true
    override val trackerJobInterval: Duration = 200.milliseconds
    override val announcesFileLoadViaEvent: Boolean = true

    private val pipeline = DesktopFramePipeline()

    /* ------------------------------ property helpers ------------------------------ */

    private fun getDouble(name: String): Double? {
        val h = handle ?: return null
        val out = Memory(8)
        return if (mpv.mpv_get_property(h, name, LibMpv.FORMAT_DOUBLE, out) >= 0) out.getDouble(0) else null
    }

    private fun getLong(name: String): Long? {
        val h = handle ?: return null
        val out = Memory(8)
        return if (mpv.mpv_get_property(h, name, LibMpv.FORMAT_INT64, out) >= 0) out.getLong(0) else null
    }

    private fun getFlag(name: String): Boolean? {
        val h = handle ?: return null
        val out = Memory(4)
        return if (mpv.mpv_get_property(h, name, LibMpv.FORMAT_FLAG, out) >= 0) out.getInt(0) != 0 else null
    }

    private fun getString(name: String): String? {
        val h = handle ?: return null
        val p = mpv.mpv_get_property_string(h, name) ?: return null
        val s = p.getString(0, "UTF-8")
        mpv.mpv_free(p)
        return s
    }

    private fun setString(name: String, value: String) {
        handle?.let { mpv.mpv_set_property_string(it, name, value) }
    }

    private fun setDouble(name: String, value: Double) {
        val h = handle ?: return
        val mem = Memory(8).apply { setDouble(0, value) }
        mpv.mpv_set_property(h, name, LibMpv.FORMAT_DOUBLE, mem)
    }

    private fun setFlag(name: String, value: Boolean) {
        val h = handle ?: return
        val mem = Memory(4).apply { setInt(0, if (value) 1 else 0) }
        mpv.mpv_set_property(h, name, LibMpv.FORMAT_FLAG, mem)
    }

    private fun command(vararg args: String) {
        val h = handle ?: return
        @Suppress("UNCHECKED_CAST")
        mpv.mpv_command(h, args.toList().toTypedArray() as Array<String?>)
    }

    /* --------------------------------- lifecycle ---------------------------------- */

    @UiThread
    override fun initialize() {
        playerScopeIO.launch {
            runCatching {
                val h = mpv.mpv_create() ?: error("mpv_create failed")

                val confDir = File(desktopAppDataDir, "mpv").apply { mkdirs() }
                mpv.mpv_set_option_string(h, "config", "yes")
                mpv.mpv_set_option_string(h, "config-dir", confDir.absolutePath)
                mpv.mpv_set_option_string(h, "vo", "libmpv")
                mpv.mpv_set_option_string(h, "idle", "yes")
                // PC-parity: at EOF mpv pauses on the last frame and time-pos stays valid,
                // instead of unloading the file (which broke position reporting on VLCJ).
                mpv.mpv_set_option_string(h, "keep-open", "yes")
                mpv.mpv_set_option_string(h, "input-default-bindings", "no")
                mpv.mpv_set_option_string(h, "terminal", "no")
                // The sw render API consumes CPU frames; auto-copy hw decoding still gives
                // CPU-accessible frames, plain "auto" could pick zero-copy paths.
                mpv.mpv_set_option_string(h, "hwdec", if (MPV_HARDWARE_ACCELERATION.valueSafe()) "auto-copy" else "no")
                mpv.mpv_set_option_string(
                    h, "screenshot-directory",
                    File(System.getProperty("user.home"), "Pictures").absolutePath
                )

                val initResult = mpv.mpv_initialize(h)
                check(initResult >= 0) { "mpv_initialize: ${mpv.mpv_error_string(initResult)}" }

                mpv.mpv_observe_property(h, 1, "pause", LibMpv.FORMAT_FLAG)
                mpv.mpv_observe_property(h, 2, "duration", LibMpv.FORMAT_DOUBLE)
                mpv.mpv_observe_property(h, 3, "eof-reached", LibMpv.FORMAT_FLAG)

                val ctx = createSwRenderContext(h)

                running = true
                handle = h
                renderCtx = ctx

                startEventThread(h)
                startRenderThread(ctx)
                mpv.mpv_render_context_set_update_callback(ctx, renderUpdateCallback, null)

                withContext(Dispatchers.Main.immediate) {
                    isInitialized = true
                    startTrackingProgress()
                }
                loggy("MpvJvmImpl: libmpv ready (${getString("mpv-version")}, from ${MpvNativeLoader.loadedFrom})")
            }.onFailure {
                loggy("MpvJvmImpl: initialization failed: ${it.stackTraceToString()}")
            }
        }
    }

    /** MPV_HARDWARE_ACCELERATION with a safe fallback when the datastore has no value yet. */
    private fun Pref<Boolean>.valueSafe(): Boolean =
        runCatching { value() }.getOrDefault(true)

    private fun createSwRenderContext(h: Pointer): Pointer {
        val apiType = Memory(3L).apply { setString(0, "sw") }
        val params = LibMpv.renderParams(
            LibMpv.RENDER_PARAM_API_TYPE to apiType,
        )
        val out = Memory(Native.POINTER_SIZE.toLong())
        val r = mpv.mpv_render_context_create(out, h, params)
        check(r >= 0) { "mpv_render_context_create: ${mpv.mpv_error_string(r)}" }
        return out.getPointer(0)
    }

    override suspend fun destroy() {
        if (!isInitialized) return
        isInitialized = false
        running = false
        playerSupervisorJob.cancel()
        mediaLoaded = false

        withContext(Dispatchers.Main.immediate) {
            runCatching {
                val ctx = renderCtx
                val h = handle
                renderCtx = null
                handle = null
                // Order matters (render.h): drop the callback, free the render context,
                // only then terminate the core. The event thread exits on EVENT_SHUTDOWN.
                if (ctx != null) {
                    mpv.mpv_render_context_set_update_callback(ctx, null, null)
                    synchronized(renderSignal) { renderSignal.notifyAll() }
                    mpv.mpv_render_context_free(ctx)
                }
                if (h != null) mpv.mpv_terminate_destroy(h)
            }.onFailure { loggy("MpvJvmImpl destroy failure: ${it.stackTraceToString()}") }
            pipeline.reset()
        }
    }

    /* ------------------------------ event loop thread ------------------------------ */

    private fun startEventThread(h: Pointer) {
        thread(name = "mpv-events", isDaemon = true) {
            while (running) {
                val event = runCatching { mpv.mpv_wait_event(h, 0.5) }.getOrNull() ?: break
                when (event.event_id) {
                    LibMpv.EVENT_SHUTDOWN -> break

                    LibMpv.EVENT_PROPERTY_CHANGE -> {
                        val data = event.data ?: continue
                        val prop = LibMpv.MpvEventProperty(data)
                        val name = prop.name?.getString(0) ?: continue
                        handlePropertyChange(name, prop.format, prop.data)
                    }

                    LibMpv.EVENT_END_FILE -> {
                        // With keep-open this only fires on stop/replace, but guard anyway.
                        if (mediaLoaded) viewmodel.playerManager.isNowPlaying.value = false
                    }
                }
            }
        }
    }

    private fun handlePropertyChange(name: String, format: Int, data: Pointer?) {
        when (name) {
            "pause" -> {
                if (format != LibMpv.FORMAT_FLAG || data == null) return
                val paused = data.getInt(0) != 0
                if (mediaLoaded) viewmodel.playerManager.isNowPlaying.value = !paused
            }

            "duration" -> {
                if (format != LibMpv.FORMAT_DOUBLE || data == null) return
                val duration = data.getDouble(0)
                if (duration <= 0.0 || !mediaLoaded) return

                viewmodel.playerManager.timeFullMillis.value = (duration * 1000).toLong()

                if (viewmodel.isSoloMode) return

                if (duration != viewmodel.media?.fileDuration) {
                    viewmodel.media?.fileDuration = duration
                    announceFileLoaded()
                }
            }

            "eof-reached" -> {
                if (format != LibMpv.FORMAT_FLAG || data == null) return
                if (data.getInt(0) != 0 && mediaLoaded) {
                    viewmodel.playerManager.isNowPlaying.value = false
                    playerScopeMain.launch {
                        pause()
                        onPlaybackEnded()
                    }
                }
            }
        }
    }

    /* -------------------------------- render thread -------------------------------- */

    private val renderSignal = Object()
    @Volatile private var renderPending = false

    /** Called by mpv on one of ITS threads; must only signal, never render or call back in. */
    private val renderUpdateCallback = object : LibMpv.RenderUpdateCallback {
        override fun invoke(callbackCtx: Pointer?) {
            synchronized(renderSignal) {
                renderPending = true
                renderSignal.notifyAll()
            }
        }
    }

    private fun startRenderThread(ctx: Pointer) {
        thread(name = "mpv-render", isDaemon = true) {
            val formatMem = Memory(5L).apply { setString(0, "bgra") }
            var sizeMem: Memory? = null
            var strideMem: Memory? = null
            var frameBuffer: Memory? = null
            var bufW = 0
            var bufH = 0

            while (running) {
                synchronized(renderSignal) {
                    while (!renderPending && running) renderSignal.wait(250)
                    renderPending = false
                }
                if (!running) break

                runCatching {
                    val flags = mpv.mpv_render_context_update(ctx)
                    if (flags and LibMpv.RENDER_UPDATE_FRAME == 0L) return@runCatching

                    val w = (getLong("dwidth") ?: 0L).toInt()
                    val h = (getLong("dheight") ?: 0L).toInt()
                    if (w <= 0 || h <= 0) return@runCatching

                    if (w != bufW || h != bufH || frameBuffer == null) {
                        bufW = w
                        bufH = h
                        frameBuffer = Memory(w.toLong() * h * 4)
                        sizeMem = Memory(8L).apply { setInt(0, w); setInt(4, h) }
                        // size_t stride
                        strideMem = Memory(8L).apply { setLong(0, w.toLong() * 4) }
                    }

                    val params = LibMpv.renderParams(
                        LibMpv.RENDER_PARAM_SW_SIZE to sizeMem,
                        LibMpv.RENDER_PARAM_SW_FORMAT to formatMem,
                        LibMpv.RENDER_PARAM_SW_STRIDE to strideMem,
                        LibMpv.RENDER_PARAM_SW_POINTER to frameBuffer,
                    )
                    val r = mpv.mpv_render_context_render(ctx, params)
                    if (r < 0) {
                        loggy("MpvJvmImpl: render failed: ${mpv.mpv_error_string(r)}")
                        return@runCatching
                    }

                    val buf = frameBuffer ?: return@runCatching
                    pipeline.deliver(w, h, w * 4) { dst ->
                        buf.read(0, dst, 0, dst.size)
                    }
                }.onFailure { loggy("MpvJvmImpl render thread: ${it.message}") }
            }
        }
    }

    /* --------------------------------- rendering UI -------------------------------- */

    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        LaunchedEffect(Unit) {
            if (!isInitialized) {
                initialize()
                while (!isInitialized) delay(50)
                onPlayerReady()
            }
        }

        // Aspect handling goes through mpv itself (video-aspect-override / panscan change the
        // rendered dwidth/dheight), so the canvas always letterbox-FITs the delivered frame.
        FrameCanvas(pipeline, { DesktopAspectMode.FIT }, modifier)
    }

    /* ---------------------------------- playback ----------------------------------- */

    private suspend fun awaitEngine(): Pointer? {
        val deadline = generateTimestampMillis() + 60_000
        while (!isInitialized && generateTimestampMillis() < deadline) delay(50)
        return handle.also {
            if (it == null) loggy("MpvJvmImpl: engine not ready after 60s — media load dropped")
        }
    }

    private var lastKnownTimeMs = 0L

    override suspend fun hasMedia(): Boolean =
        isInitialized && mediaLoaded && (getLong("playlist-count") ?: 0L) > 0L

    override suspend fun isPlaying(): Boolean {
        if (!isInitialized) return false
        return getFlag("pause") == false
    }

    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
        awaitEngine() ?: return
        installMpvSubfontIfNeeded()
        lastKnownTimeMs = 0L
        mediaLoaded = true
        command("loadfile", File(location.file.path).absolutePath, "replace")
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        awaitEngine() ?: return
        installMpvSubfontIfNeeded()
        lastKnownTimeMs = 0L
        mediaLoaded = true
        command("loadfile", location.url, "replace")
    }

    override suspend fun pause() {
        if (!isInitialized) return
        setFlag("pause", true)
    }

    override suspend fun play() {
        if (!isInitialized) return
        setFlag("pause", false)
    }

    override suspend fun setSpeed(speed: Double) {
        if (!isInitialized) return
        setDouble("speed", speed)
    }

    override suspend fun isSeekable(): Boolean {
        if (!isInitialized) return false
        return getFlag("seekable") ?: true
    }

    @UiThread
    override fun seekTo(toPositionMs: Long) {
        if (!isInitialized) return
        super.seekTo(toPositionMs)
        // Sub-second precision via the double property, mirroring the Android engine.
        setDouble("time-pos", toPositionMs.toDouble() / 1000.0)
    }

    override fun currentPositionMs(): Long {
        if (!isInitialized) return 0L
        val precise = getDouble("time-pos") ?: return lastKnownTimeMs
        lastKnownTimeMs = (precise * 1000.0).toLong()
        return lastKnownTimeMs
    }

    /* ----------------------------------- tracks ------------------------------------ */

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return
        viewmodel.media?.tracks?.clear()

        val count = getLong("track-list/count")?.toInt() ?: return
        for (i in 0 until count) {
            val type = getString("track-list/$i/type") ?: continue
            if (type != "audio" && type != "sub") continue
            val mpvId = getLong("track-list/$i/id")?.toInt() ?: continue
            val lang = getString("track-list/$i/lang")
            val title = getString("track-list/$i/title")
            val selected = getFlag("track-list/$i/selected") ?: false

            val trackName = when {
                !title.isNullOrEmpty() && !lang.isNullOrEmpty() -> "$title [$lang]"
                !title.isNullOrEmpty() -> "$title [UND]"
                !lang.isNullOrEmpty() -> "Track [$lang]"
                else -> "Track $mpvId [UND]"
            }

            viewmodel.media?.tracks?.add(
                MpvJvmTrack(
                    name = trackName,
                    type = if (type == "audio") TrackType.AUDIO else TrackType.SUBTITLE,
                    index = mpvId,
                    selected = selected
                )
            )
        }
    }

    override suspend fun selectTrack(track: Track?, type: TrackType) {
        if (!isInitialized) return
        when (type) {
            TrackType.SUBTITLE -> {
                if (track != null) setString("sid", track.index.toString()) else setString("sid", "no")
                playerManager.currentTrackChoices.subtitleSelectionIndexMpv = track?.index ?: -1
            }

            TrackType.AUDIO -> {
                if (track != null) setString("aid", track.index.toString()) else setString("aid", "no")
                playerManager.currentTrackChoices.audioSelectionIndexMpv = track?.index ?: -1
            }
        }
    }

    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return
        val subIndex = playerManager.currentTrackChoices.subtitleSelectionIndexMpv
        val audioIndex = playerManager.currentTrackChoices.audioSelectionIndexMpv

        val ccGet = playerManager.media.value?.tracks?.filter { it.type == TrackType.SUBTITLE }
            ?.firstOrNull { it.index == subIndex }
        val audioGet = playerManager.media.value?.tracks?.filter { it.type == TrackType.AUDIO }
            ?.firstOrNull { it.index == audioIndex }

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

    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
        if (!isInitialized) return
        command("sub-add", File(uri.path).absolutePath, "cached")
    }

    /* ---------------------------------- chapters ----------------------------------- */

    override suspend fun analyzeChapters(mediafile: MediaFile) {
        if (!isInitialized) return
        mediafile.chapters.clear()

        val count = getLong("chapter-list/count")?.toInt() ?: return
        for (i in 0 until count) {
            val title = getString("chapter-list/$i/title")
            val time = getDouble("chapter-list/$i/time") ?: continue
            mediafile.chapters.add(
                Chapter(
                    index = i,
                    name = title ?: "Chapter $i",
                    timeOffsetMillis = (time * 1000).roundToLong()
                )
            )
        }
    }

    override suspend fun jumpToChapter(chapter: Chapter) {
        if (!isInitialized) return
        super.jumpToChapter(chapter)
        setString("chapter", chapter.index.toString())
    }

    /* ----------------------------------- misc -------------------------------------- */

    override suspend fun takeScreenshot(): Boolean {
        if (!isInitialized) return false
        command("screenshot")
        return true
    }

    override suspend fun switchAspectRatio(): String {
        if (!isInitialized) return "NO PLAYER FOUND"

        val currentAspect = getString("video-aspect-override")
        val currentPanscan = getDouble("panscan")

        val aspectRatios = listOf(
            "-1.000000" to "Original", "1.777778" to "16:9",
            "1.600000" to "16:10", "1.333333" to "4:3",
            "2.350000" to "2.35:1", "panscan" to "Pan/Scan"
        )

        var enablePanscan = false
        val nextAspect = if (currentPanscan == 1.0) {
            aspectRatios[0]
        } else if (currentAspect == "2.350000") {
            enablePanscan = true
            aspectRatios[5]
        } else {
            val idx = aspectRatios.indexOfFirst { it.first == currentAspect }
            aspectRatios[(idx + 1).coerceIn(0, aspectRatios.size - 1)]
        }

        if (enablePanscan) {
            setString("video-aspect-override", "-1")
            setDouble("panscan", 1.0)
        } else {
            setString("video-aspect-override", nextAspect.first)
            setDouble("panscan", 0.0)
        }

        return nextAspect.second
    }

    override suspend fun changeSubtitleSize(newSize: Int) {
        if (!isInitialized) return
        val s: Double = when {
            newSize == 16 -> 1.0
            newSize > 16 -> 1.0 + (newSize - 16) * 0.05
            else -> 1.0 - (16 - newSize) * (1.0 / 16)
        }
        setDouble("sub-scale", s)
    }

    /* mpv softvol: 0..100 nominal, up to volume-max (130 default). */
    override fun getMaxVolume() = 130
    override fun getCurrentVolume(): Int = getDouble("volume")?.toInt() ?: 0
    override fun changeCurrentVolume(v: Int) {
        setDouble("volume", v.coerceIn(0, 130).toDouble())
    }

    /* ---------------------------------- settings ----------------------------------- */

    override suspend fun configurableSettings() = SettingCategory(
        title = Res.string.uisetting_categ_mpv,
        icon = Icons.Filled.SettingsInputComponent
    ) {
        +MPV_HARDWARE_ACCELERATION.apply {
            config?.extraConfig = PrefExtraConfig.BooleanCallback { b ->
                setString("hwdec", if (b) "auto-copy" else "no")
            }
        }
        +Pref("mpv_desktop_subtitle_delay_ms", 0) {
            title = Res.string.uisetting_subtitle_delay_title
            summary = Res.string.uisetting_subtitle_delay_summary
            icon = Icons.Filled.ClosedCaptionOff
            extraConfig = PrefExtraConfig.Slider(minValue = -5000, maxValue = 5000) {
                setDouble("sub-delay", it / 1000.0)
            }
        }
        +Pref("mpv_desktop_audio_delay_ms", 0) {
            title = Res.string.uisetting_audio_delay_title
            summary = Res.string.uisetting_audio_delay_summary
            icon = Icons.Filled.SpatialAudio
            extraConfig = PrefExtraConfig.Slider(minValue = -5000, maxValue = 5000) {
                setDouble("audio-delay", it / 1000.0)
            }
        }
        // mpv.conf import/export — desktop reads a real user config dir (config-dir above).
        +MPV_IMPORT_CONF
        +MPV_EXPORT_CONF
    }
}
