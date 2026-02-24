package com.yuroyami.syncplay.managers.player.vlc

import SyncplayMobile.shared.BuildConfig
import android.content.Context
import android.media.AudioManager
import android.view.LayoutInflater
import androidx.annotation.MainThread
import androidx.annotation.UiThread
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C.STREAM_TYPE_MUSIC
import com.yuroyami.syncplay.R
import com.yuroyami.syncplay.managers.player.PlayerImpl
import com.yuroyami.syncplay.managers.player.VideoEngine
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.MediaFileLocation
import com.yuroyami.syncplay.utils.GlobalPlayerSession
import com.yuroyami.syncplay.utils.contextObtainer
import com.yuroyami.syncplay.utils.uri
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMedia.Track
import org.videolan.libvlc.util.VLCVideoLayout
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.vlc
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * VLC - Popular cross-platform media player with maximum format support.
 *
 * **Characteristics:**
 * - Widest format support including QuickTime, Xvid, and obscure codecs
 * - Very powerful but least stable (potential crashes/bugs)
 * - Large library size
 * - Requires native libraries
 *
 * **Best for:** Users who need to play uncommon/legacy formats despite stability risks
 *
 * **Availability:** Only in withLibs build flavor
 */
object VlcEngine: VideoEngine {
    override val isAvailable: Boolean = !BuildConfig.EXOPLAYER_ONLY
    override val isDefault: Boolean = false
    override val name: String = "VLC"
    override val img: DrawableResource = Res.drawable.vlc

    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = object : PlayerImpl(viewmodel, this@VlcEngine) {
            lateinit var audioManager: AudioManager

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
                audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                libvlc = LibVLC(ctx, listOf("-vv"))
                vlcPlayer = MediaPlayer(libvlc)
                vlcPlayer?.attachViews(vlcView, null, true, true)

                isInitialized = true

                vlcAttachObserver()

                startTrackingProgress()
            }

            override suspend fun destroy() {
                if (!isInitialized) return

                withContext(Dispatchers.Main.immediate) {
                    vlcPlayer?.stop()
                    vlcMedia?.release()
                    vlcPlayer?.release()
                    libvlc?.release()
                }
            }


            override fun initMediaSession(): GlobalPlayerSession {
                TODO("Not yet implemented")
            }

            override fun finalizeMediaSession() {
                TODO("Not yet implemented")
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

            //TODO
            override suspend fun configurableSettings() = null

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

            override suspend fun selectTrack(track: com.yuroyami.syncplay.models.Track?, type: TrackType) {
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

                            viewmodel.videoEngineManager.currentTrackChoices.subtitleSelectionIdVlc = vlcTrack?.id ?: "-1"
                        }

                        TrackType.AUDIO -> {
                            if (vlcTrack != null) {
                                vlcPlayer?.selectTrack(vlcTrack.id)
                            } else {
                                vlcPlayer?.unselectTrackType(Track.Type.Audio)
                            }

                            viewmodel.videoEngineManager.currentTrackChoices.audioSelectionIdVlc = vlcTrack?.id ?: "-1"
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

                val subId = viewmodel.videoEngineManager.currentTrackChoices.subtitleSelectionIdVlc
                val audioId = viewmodel.videoEngineManager.currentTrackChoices.audioSelectionIdVlc

                val tracks = videoEngineManager.media.value?.tracks

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
                        MediaPlayer.Event.Playing -> {
                            if (vlcPlayer?.hasMedia() == true) {
                                viewmodel.videoEngineManager.isNowPlaying.value = true //Just to inform UI

                                //Tell server about playback state change
                                if (!viewmodel.isSoloMode) {
                                    viewmodel.actionManager.sendPlayback(true)
                                }
                            }
                        }

                        MediaPlayer.Event.Paused -> {
                            if (vlcPlayer?.hasMedia() == true) {
                                viewmodel.videoEngineManager.isNowPlaying.value = false //Just to inform UI

                                //Tell server about playback state change
                                if (!viewmodel.isSoloMode) {
                                    viewmodel.actionManager.sendPlayback(false)
                                }
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

                                viewmodel.videoEngineManager.timeFullMillis.value = abs(durationMs)

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

            override fun getMaxVolume() = audioManager.getStreamMaxVolume(STREAM_TYPE_MUSIC)
            override fun getCurrentVolume() = audioManager.getStreamVolume(STREAM_TYPE_MUSIC)
            override fun changeCurrentVolume(v: Int) {
                if (!audioManager.isVolumeFixed) {
                    audioManager.setStreamVolume(STREAM_TYPE_MUSIC, v, 0)
                }
            }
        }
}