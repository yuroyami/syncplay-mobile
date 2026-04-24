package app.player.vlc

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.annotation.UiThread
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.R
import app.player.PlayerImpl
import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.preferences.Pref
import app.preferences.PrefExtraConfig
import app.preferences.Preferences.VLC_CUSTOM_FLAGS
import app.preferences.settings.SettingCategory
import app.room.RoomViewmodel
import app.utils.contextObtainer
import app.utils.uri
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMedia.Track
import org.videolan.libvlc.util.VLCVideoLayout
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.uisetting_audio_delay_summary
import syncplaymobile.shared.generated.resources.uisetting_audio_delay_title
import syncplaymobile.shared.generated.resources.uisetting_categ_vlc
import syncplaymobile.shared.generated.resources.uisetting_subtitle_delay_summary
import syncplaymobile.shared.generated.resources.uisetting_subtitle_delay_title
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class VlcImpl(vm: RoomViewmodel) : PlayerImpl(vm, VlcEngine) {
    private val ctx = contextObtainer()

    private var libvlc: LibVLC? = null
    private var vlcPlayer: MediaPlayer? = null
    private lateinit var vlcView: VLCVideoLayout

    private var vlcMedia: Media? = null

    override val supportsChapters: Boolean = true
    override val trackerJobInterval: Duration = 200.milliseconds

    //TODO CHANGE HOW TIME IS TRACKED

    @UiThread
    override fun initialize() {
        // "-vv" keeps the default verbose logging; any user-supplied flags from
        // Preferences.VLC_CUSTOM_FLAGS are appended verbatim so they can override LibVLC defaults.
        libvlc = LibVLC(ctx, listOf("-vv") + app.utils.vlcCustomFlags())
        vlcPlayer = MediaPlayer(libvlc)
        vlcPlayer?.attachViews(vlcView, null, true, true)

        // Force the default scale (preserves aspect ratio and centers the video).
        vlcPlayer?.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT

        // Workaround for a libVLC 4 eap centering issue: the TextureView/SurfaceView that
        // attachViews() inserts into VLCVideoLayout gets laid out with default FrameLayout
        // gravity (TOP|START), so when the scaled video is narrower than the parent, the
        // black bar ends up on the right side only. Force center-gravity on every child
        // surface any time the layout changes so both the initial layout and any later
        // relayout (orientation change, etc.) stay centered. */
        vlcView.viewTreeObserver.addOnGlobalLayoutListener { centerVlcSurfaces(vlcView) }
        centerVlcSurfaces(vlcView)

        isInitialized = true

        vlcAttachObserver()

        startTrackingProgress()
    }

    private fun centerVlcSurfaces(root: View) {
        if (root !is ViewGroup) return
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            val lp = child.layoutParams
            if (lp is FrameLayout.LayoutParams && lp.gravity != Gravity.CENTER) {
                lp.gravity = Gravity.CENTER
                child.layoutParams = lp
            }
            if (child is ViewGroup) centerVlcSurfaces(child)
        }
    }

    override suspend fun destroy() {
        if (!isInitialized) return
        isInitialized = false
        playerSupervisorJob.cancel()

        withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.stop()
            vlcMedia?.release()
            vlcPlayer?.release()
            libvlc?.release()
            vlcMedia = null
            vlcPlayer = null
            libvlc = null
        }
    }

    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                vlcView = LayoutInflater.from(context).inflate(R.layout.vlcview, null) as VLCVideoLayout
                initialize()
                onPlayerReady()
                return@AndroidView vlcView
            },
            onRelease = {
                //vlcPlayer?.detachViews()
            }
        )
    }

    override suspend fun configurableSettings() = SettingCategory(
        title = Res.string.uisetting_categ_vlc,
        icon = Icons.Filled.SettingsInputComponent
    ) {
        +Pref("vlc_subtitle_delay_ms", 0) {
            title = Res.string.uisetting_subtitle_delay_title
            summary = Res.string.uisetting_subtitle_delay_summary
            icon = Icons.Filled.ClosedCaptionOff
            extraConfig = PrefExtraConfig.Slider(minValue = -5000, maxValue = 5000) {
                vlcPlayer?.setSpuDelay(it * 1000L)
            }
        }
        +Pref("vlc_audio_delay_ms", 0) {
            title = Res.string.uisetting_audio_delay_title
            summary = Res.string.uisetting_audio_delay_summary
            icon = Icons.Filled.SpatialAudio
            extraConfig = PrefExtraConfig.Slider(minValue = -5000, maxValue = 5000) {
                vlcPlayer?.setAudioDelay(it * 1000L)
            }
        }
        // Custom LibVLC launch flags — only meaningful when the VLC engine is actually the
        // one constructing a LibVLC instance, so attached to the engine-specific category.
        +VLC_CUSTOM_FLAGS
    }

    override suspend fun hasMedia(): Boolean {
        if (!isInitialized) return false

        return withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.hasMedia() == true
        }
    }

    override suspend fun isPlaying(): Boolean {
        if (!isInitialized) return false

        return withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.isPlaying == true
        }
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            viewmodel.media?.tracks?.clear()

            val audioTracks = vlcPlayer?.getTracks(Track.Type.Audio) ?: emptyArray()
            val subtitleTracks = vlcPlayer?.getTracks(Track.Type.Text) ?: emptyArray()
            (audioTracks + subtitleTracks).forEachIndexed { i, vlcTrack ->
                viewmodel.media?.tracks?.add(
                    VlcTrack(
                        name = vlcTrack.name,
                        type = if (vlcTrack.type == Track.Type.Audio) TrackType.AUDIO else TrackType.SUBTITLE,
                        index = i,
                        id = vlcTrack.id,
                        selected = vlcTrack.selected,
                    )
                )
            }
        }
    }

    override suspend fun selectTrack(track: app.player.models.Track?, type: TrackType) {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            val vlcTrack = track as? VlcTrack

            when (type) {
                TrackType.SUBTITLE -> {
                    if (vlcTrack != null) {
                        vlcPlayer?.selectTrack(vlcTrack.id)
                    } else {
                        vlcPlayer?.unselectTrackType(Track.Type.Text)
                    }

                    viewmodel.playerManager.currentTrackChoices.subtitleSelectionIdVlc = vlcTrack?.id ?: "-1"
                }

                TrackType.AUDIO -> {
                    if (vlcTrack != null) {
                        vlcPlayer?.selectTrack(vlcTrack.id)
                    } else {
                        vlcPlayer?.unselectTrackType(Track.Type.Audio)
                    }

                    viewmodel.playerManager.currentTrackChoices.audioSelectionIdVlc = vlcTrack?.id ?: "-1"
                }
            }
        }
    }

    override suspend fun analyzeChapters(mediafile: MediaFile) {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            mediafile.chapters.clear()
            val chapters = vlcPlayer?.getChapters(-1)
            chapters?.forEachIndexed { i, chptr ->
                mediafile.chapters.add(
                    Chapter(
                        index = i,
                        name = chptr.name,
                        timeOffsetMillis = chptr.timeOffset
                    )
                )
            }
        }
    }

    override suspend fun jumpToChapter(chapter: Chapter) {
        if (!isInitialized) return
        super.jumpToChapter(chapter)

        withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.chapter = chapter.index
        }
    }
//
//    override suspend fun skipChapter() {
//        if (!isInitialized) return
//
//        withContext(Dispatchers.Main.immediate) {
//            vlcPlayer?.nextChapter()
//        }
//    }

    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return

        val subId = viewmodel.playerManager.currentTrackChoices.subtitleSelectionIdVlc
        val audioId = viewmodel.playerManager.currentTrackChoices.audioSelectionIdVlc

        val tracks = playerManager.media.value?.tracks

        val ccMap = tracks?.filter { it.type == TrackType.SUBTITLE }?.map { it as VlcTrack }
        val audioMap = tracks?.filter { it.type == TrackType.AUDIO }?.map { it as VlcTrack }

        val ccGet = ccMap?.firstOrNull { it.id == subId }
        val audioGet = audioMap?.firstOrNull { it.id == audioId }

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
            try {
                vlcPlayer?.addSlave(
                    IMedia.Slave.Type.Subtitle, uri.path, true
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
        val desc = contextObtainer().contentResolver.openFileDescriptor(location.file.uri, "r")
        val media = Media(libvlc, desc?.fileDescriptor)
        media.parse()
        //todo: global property to switch hw/sw
        vlcPlayer?.play(media)
        vlcMedia = media
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        vlcPlayer?.play(location.url)
    }

    override suspend fun pause() {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.pause()
        }
    }

    override suspend fun play() {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.play()
        }
    }

    override suspend fun setSpeed(speed: Double) {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.rate = speed.toFloat()
        }
    }

    override suspend fun isSeekable(): Boolean {
        if (!isInitialized) return false

        return withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.isSeekable == true
        }
    }

    @UiThread
    override fun seekTo(toPositionMs: Long) {
        if (!isInitialized) return

        super.seekTo(toPositionMs)
        vlcPlayer?.setTime(toPositionMs, true)
    }

    @MainThread
    override fun currentPositionMs(): Long {
        if (!isInitialized) return 0L

        return vlcPlayer?.time ?: 0L
    }

    override suspend fun switchAspectRatio(): String {
        if (!isInitialized) return "NO PLAYER FOUND"

        val scaleTypes = MediaPlayer.ScaleType.getMainScaleTypes()

        val currentScale = vlcPlayer?.videoScale
        val nextScaleIndex = scaleTypes.indexOf(currentScale) + 1
        vlcPlayer?.videoScale = if (nextScaleIndex == scaleTypes.size) {
            scaleTypes[0]
        } else {
            scaleTypes[nextScaleIndex]
        }

        return vlcPlayer?.videoScale?.name ?: "ORIGINAL"
    }

    override suspend fun changeSubtitleSize(newSize: Int) {
        //TODO
    }

    /** VLC EXCLUSIVE */
    private fun vlcAttachObserver() {
        vlcPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing, MediaPlayer.Event.Paused -> {
                    if (vlcPlayer?.hasMedia() == true) {
                        viewmodel.playerManager.isNowPlaying.value = vlcPlayer?.isPlaying == true //Just to inform UI
                    }
                }

                MediaPlayer.Event.EndReached -> {
                    playerScopeMain.launch {
                        pause()
                        onPlaybackEnded()
                    }
                }

                MediaPlayer.Event.LengthChanged -> {
                    if (vlcPlayer?.hasMedia() == true) {
                        /* Updating our timeFull */
                        val durationMs = vlcPlayer!!.length

                        viewmodel.playerManager.timeFullMillis.value = abs(durationMs)

                        if (viewmodel.isSoloMode) return@setEventListener

                        if (durationMs / 1000.0 != viewmodel.media?.fileDuration) {
                            viewmodel.media?.fileDuration = durationMs / 1000.0

                            announceFileLoaded()
                        }
                    }
                }
            }
        }

    }

    override fun getMaxVolume() = 200
    override fun getCurrentVolume(): Int = vlcPlayer?.volume ?: 0
    override fun changeCurrentVolume(v: Int) {
        vlcPlayer?.volume = v.coerceIn(0, 200)
    }
}
