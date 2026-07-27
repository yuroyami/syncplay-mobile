package app.player.vlcj

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.annotation.UiThread
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SpatialAudio
import app.player.DesktopAspectMode
import app.player.DesktopFramePipeline
import app.player.FrameCanvas
import app.player.PlayerImpl
import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.preferences.Pref
import app.preferences.PrefExtraConfig
import app.preferences.Preferences.VLC_CUSTOM_FLAGS
import app.preferences.settings.SettingCategory
import app.room.RoomViewmodel
import app.utils.loggy
import app.utils.vlcCustomFlags
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.uisetting_audio_delay_summary
import syncplaymobile.shared.generated.resources.uisetting_audio_delay_title
import syncplaymobile.shared.generated.resources.uisetting_categ_vlc
import syncplaymobile.shared.generated.resources.uisetting_subtitle_delay_summary
import syncplaymobile.shared.generated.resources.uisetting_subtitle_delay_title
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.MediaSlaveType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Desktop libVLC engine via vlcj.
 *
 * Rendering: libVLC decodes into CPU RV32 buffers (CallbackVideoSurface); each frame is copied
 * into a reused Skia bitmap and drawn by a Compose Canvas that invalidates on a frame counter.
 * No AWT/Swing surface embedding — the HUD overlays the video like on mobile, with zero
 * heavyweight z-order problems. The double memcpy is well within budget for 1080p playback.
 */
class VlcjImpl(vm: RoomViewmodel) : PlayerImpl(vm, VlcjEngine) {

    private var factory: MediaPlayerFactory? = null
    private var player: EmbeddedMediaPlayer? = null

    /** vlcj/libVLC has no cheap synchronous has-media probe; track it ourselves. */
    @Volatile private var mediaLoaded = false

    override val supportsChapters: Boolean = true
    override val supportsPictureInPicture: Boolean = false
    override val trackerJobInterval: Duration = 200.milliseconds

    /* libVLC 3 announces duration via the LengthChanged event (see the event adapter below),
     * like the Android libVLC engine. Only consulted on iOS, but kept truthful. */
    override val announcesFileLoadViaEvent: Boolean = true

    /* ------------------------------- frame pipeline ------------------------------- */

    private val pipeline = DesktopFramePipeline()
    private var aspectMode = DesktopAspectMode.FIT

    private val bufferFormatCallback = object : BufferFormatCallbackAdapter() {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            pipeline.reset() // realloc lazily on the next display()
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }
    }

    private val renderCallback = RenderCallback { _, nativeBuffers, bufferFormat ->
        val src: ByteBuffer = nativeBuffers?.getOrNull(0) ?: return@RenderCallback
        val w = bufferFormat.width
        val h = bufferFormat.height
        val rowBytes = bufferFormat.pitches.getOrNull(0) ?: (w * 4)
        pipeline.deliver(w, h, rowBytes) { dst ->
            src.rewind()
            src.get(dst, 0, minOf(src.remaining(), dst.size))
        }
    }

    /* --------------------------------- lifecycle ---------------------------------- */

    @UiThread
    override fun initialize() {
        // libVLC construction MUST stay off the EDT. NativeDiscovery + factory init load the
        // dylibs and rescan the plugin cache — seconds of native work that would freeze the UI
        // and, on macOS, can wedge AWT dispatch entirely (observed as the protocol consumer's
        // Main hop never resuming). Build everything on IO, then publish on Main.
        playerScopeIO.launch {
            runCatching {
                // MediaPlayerFactory runs vlcj NativeDiscovery on construction; the ServiceLoader-
                // registered BundledVlcDirectoryProvider makes it prefer the libVLC we ship.
                val f = MediaPlayerFactory(vlcCustomFlags())
                val p = f.mediaPlayers().newEmbeddedMediaPlayer()
                p.videoSurface().set(
                    CallbackVideoSurface(
                        bufferFormatCallback,
                        renderCallback,
                        true,
                        VideoSurfaceAdapters.getVideoSurfaceAdapter()
                    )
                )
                attachObserver(p)

                withContext(Dispatchers.Main.immediate) {
                    factory = f
                    player = p
                    isInitialized = true
                    startTrackingProgress()
                }
            }.onFailure {
                loggy("VlcjImpl: libVLC initialization failed: ${it.stackTraceToString()}")
            }
        }
    }

    override suspend fun destroy() {
        if (!isInitialized) return
        isInitialized = false
        playerSupervisorJob.cancel()
        mediaLoaded = false

        withContext(Dispatchers.Main.immediate) {
            runCatching {
                player?.controls()?.stop()
                player?.release()
                factory?.release()
            }.onFailure { loggy("VlcjImpl destroy failure: ${it.stackTraceToString()}") }
            player = null
            factory = null
            pipeline.reset()
        }
    }

    /* vlcj event callbacks arrive on a libVLC native thread. Touching the player from inside
     * them must go through a coroutine hop (never synchronously), and pure StateFlow/Compose
     * state writes are thread-safe as-is. */
    private fun attachObserver(p: EmbeddedMediaPlayer) {
        p.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                if (mediaLoaded) viewmodel.playerManager.isNowPlaying.value = true
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                if (mediaLoaded) viewmodel.playerManager.isNowPlaying.value = false
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                // Reaching EOF puts libVLC in STOPPED: setPause() becomes a no-op and no
                // paused() event ever fires. Flip isNowPlaying directly so the engine-driven
                // auto-pause broadcast reaches the room — without this the room kept playing
                // while we reported a frozen position, and the server rewound everyone to us.
                viewmodel.playerManager.isNowPlaying.value = false
                playerScopeMain.launch {
                    pause()
                    onPlaybackEnded()
                }
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                if (mediaLoaded) viewmodel.playerManager.isNowPlaying.value = false
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                if (!mediaLoaded) return

                viewmodel.playerManager.timeFullMillis.value = abs(newLength)

                if (viewmodel.isSoloMode) return

                if (newLength / 1000.0 != viewmodel.media?.fileDuration) {
                    viewmodel.media?.fileDuration = newLength / 1000.0

                    announceFileLoaded()
                }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                loggy("VlcjImpl: libVLC reported a media error")
            }
        })
    }

    /* ---------------------------------- rendering ---------------------------------- */

    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        LaunchedEffect(Unit) {
            if (!isInitialized) {
                initialize()
                // initialize() completes asynchronously off the EDT; report readiness
                // only once the engine is actually usable.
                while (!isInitialized) delay(50)
                onPlayerReady()
            }
        }

        FrameCanvas(pipeline, { aspectMode }, modifier)
    }

    override suspend fun switchAspectRatio(): String {
        if (!isInitialized) return "NO PLAYER FOUND"
        val entries = DesktopAspectMode.entries
        aspectMode = entries[(entries.indexOf(aspectMode) + 1) % entries.size]
        pipeline.frameTick.longValue++ // force a redraw with the new mode even while paused
        return aspectMode.label
    }

    /* ---------------------------------- settings ----------------------------------- */

    override suspend fun configurableSettings() = SettingCategory(
        title = Res.string.uisetting_categ_vlc,
        icon = Icons.Filled.SettingsInputComponent
    ) {
        +Pref("vlc_subtitle_delay_ms", 0) {
            title = Res.string.uisetting_subtitle_delay_title
            summary = Res.string.uisetting_subtitle_delay_summary
            icon = Icons.Filled.ClosedCaptionOff
            extraConfig = PrefExtraConfig.Slider(minValue = -5000, maxValue = 5000) {
                player?.subpictures()?.setDelay(it * 1000L)
            }
        }
        +Pref("vlc_audio_delay_ms", 0) {
            title = Res.string.uisetting_audio_delay_title
            summary = Res.string.uisetting_audio_delay_summary
            icon = Icons.Filled.SpatialAudio
            extraConfig = PrefExtraConfig.Slider(minValue = -5000, maxValue = 5000) {
                player?.audio()?.setDelay(it * 1000L)
            }
        }
        +VLC_CUSTOM_FLAGS
    }

    /* ---------------------------------- playback ----------------------------------- */

    override suspend fun hasMedia(): Boolean = isInitialized && mediaLoaded

    override suspend fun isPlaying(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) {
            player?.status()?.isPlaying == true
        }
    }

    /**
     * initialize() finishes asynchronously (libVLC loads off the EDT), but isPlayerReady —
     * which gates injects at the UI layer — only means "the PlayerImpl instance exists".
     * A load issued during the init window would hit a null player and vanish silently,
     * so injects briefly await engine readiness.
     */
    private suspend fun awaitEngine(): uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer? {
        val deadline = app.utils.generateTimestampMillis() + 60_000
        while (!isInitialized && app.utils.generateTimestampMillis() < deadline) delay(50)
        return player.also {
            if (it == null) loggy("VlcjImpl: engine not ready after 60s — media load dropped")
        }
    }

    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
        val p = awaitEngine() ?: return
        lastKnownTimeMs = 0L
        mediaLoaded = true
        p.media().play(File(location.file.path).absolutePath)
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        val p = awaitEngine() ?: return
        lastKnownTimeMs = 0L
        mediaLoaded = true
        p.media().play(location.url)
    }

    override suspend fun pause() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            player?.controls()?.setPause(true)
        }
    }

    override suspend fun play() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            player?.controls()?.play()
        }
    }

    override suspend fun setSpeed(speed: Double) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            player?.controls()?.setRate(speed.toFloat())
        }
    }

    override suspend fun isSeekable(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) {
            player?.status()?.isSeekable == true
        }
    }

    @UiThread
    override fun seekTo(toPositionMs: Long) {
        if (!isInitialized) return
        super.seekTo(toPositionMs)
        player?.controls()?.setTime(toPositionMs)
    }

    /** libVLC reports time() = -1 in the STOPPED state (e.g. right after EOF). Feeding that
     *  to the sync layer advertised position -0.001 and made the server rewind the whole room
     *  to us — hold the last good position instead. */
    private var lastKnownTimeMs = 0L

    override fun currentPositionMs(): Long {
        if (!isInitialized) return 0L
        val t = player?.status()?.time() ?: return lastKnownTimeMs
        return if (t >= 0) {
            lastKnownTimeMs = t
            t
        } else lastKnownTimeMs
    }

    /* ----------------------------------- tracks ------------------------------------ */

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            viewmodel.media?.tracks?.clear()
            val p = player ?: return@withContext

            val currentAudio = p.audio().track()
            val currentSub = p.subpictures().track()

            var i = 0
            // TrackDescription lists include the synthetic "Disable" entry with id -1 — skip it.
            p.audio().trackDescriptions().filter { it.id() >= 0 }.forEach { desc ->
                viewmodel.media?.tracks?.add(
                    VlcjTrack(
                        id = desc.id().toString(),
                        name = desc.description() ?: "Audio ${desc.id()}",
                        type = TrackType.AUDIO,
                        index = i++,
                        selected = desc.id() == currentAudio,
                    )
                )
            }
            p.subpictures().trackDescriptions().filter { it.id() >= 0 }.forEach { desc ->
                viewmodel.media?.tracks?.add(
                    VlcjTrack(
                        id = desc.id().toString(),
                        name = desc.description() ?: "Subtitle ${desc.id()}",
                        type = TrackType.SUBTITLE,
                        index = i++,
                        selected = desc.id() == currentSub,
                    )
                )
            }
        }
    }

    override suspend fun selectTrack(track: app.player.models.Track?, type: TrackType) {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            val vlcjTrack = track as? VlcjTrack
            val p = player ?: return@withContext

            when (type) {
                TrackType.SUBTITLE -> {
                    p.subpictures().setTrack(vlcjTrack?.id?.toIntOrNull() ?: -1)
                    viewmodel.playerManager.currentTrackChoices.subtitleSelectionIdVlc = vlcjTrack?.id ?: "-1"
                }

                TrackType.AUDIO -> {
                    p.audio().setTrack(vlcjTrack?.id?.toIntOrNull() ?: -1)
                    viewmodel.playerManager.currentTrackChoices.audioSelectionIdVlc = vlcjTrack?.id ?: "-1"
                }
            }
        }
    }

    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return

        val subId = viewmodel.playerManager.currentTrackChoices.subtitleSelectionIdVlc
        val audioId = viewmodel.playerManager.currentTrackChoices.audioSelectionIdVlc

        val tracks = playerManager.media.value?.tracks

        val ccGet = tracks?.filter { it.type == TrackType.SUBTITLE }?.map { it as VlcjTrack }
            ?.firstOrNull { it.id == subId }
        val audioGet = tracks?.filter { it.type == TrackType.AUDIO }?.map { it as VlcjTrack }
            ?.firstOrNull { it.id == audioId }

        with(viewmodel.player) {
            if (subId == "-1") {
                selectTrack(null, TrackType.SUBTITLE)
            } else if (ccGet != null) {
                selectTrack(ccGet, TrackType.SUBTITLE)
            }

            if (audioId == "-1") {
                selectTrack(null, TrackType.AUDIO)
            } else if (audioGet != null) {
                selectTrack(audioGet, TrackType.AUDIO)
            }
        }
    }

    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
        withContext(Dispatchers.Main.immediate) {
            runCatching {
                // addSlave wants a proper MRL; NIO's toUri yields the file:/// form libVLC accepts.
                val mrl = File(uri.path).toPath().toUri().toString()
                player?.media()?.addSlave(MediaSlaveType.SUBTITLE, mrl, true)
            }.onFailure { it.printStackTrace() }
        }
    }

    /* ---------------------------------- chapters ----------------------------------- */

    override suspend fun analyzeChapters(mediafile: MediaFile) {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            mediafile.chapters.clear()
            val descriptions = player?.chapters()?.descriptions() ?: return@withContext
            descriptions.forEachIndexed { i, chapter ->
                mediafile.chapters.add(
                    Chapter(
                        index = i,
                        name = chapter.name() ?: "Chapter $i",
                        timeOffsetMillis = chapter.offset()
                    )
                )
            }
        }
    }

    override suspend fun jumpToChapter(chapter: Chapter) {
        if (!isInitialized) return
        super.jumpToChapter(chapter)

        withContext(Dispatchers.Main.immediate) {
            player?.chapters()?.setChapter(chapter.index)
        }
    }

    /* ----------------------------------- misc -------------------------------------- */

    override suspend fun changeSubtitleSize(newSize: Int) {
        // libVLC 3 only honors --freetype-rel-fontsize at startup; no live resize. TODO
    }

    override fun getMaxVolume() = 200
    override fun getCurrentVolume(): Int = player?.audio()?.volume() ?: 0
    override fun changeCurrentVolume(v: Int) {
        player?.audio()?.setVolume(v.coerceIn(0, 200))
    }
}
