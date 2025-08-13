package com.yuroyami.syncplay.logic.player.vlc

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C.STREAM_TYPE_MUSIC
import androidx.media3.common.MimeTypes
import com.yuroyami.syncplay.databinding.VlcviewBinding
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import com.yuroyami.syncplay.logic.player.AndroidPlayerEngine
import com.yuroyami.syncplay.logic.player.BasePlayer
import com.yuroyami.syncplay.logic.protocol.PacketCreator
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.utils.collectInfoLocalAndroid
import com.yuroyami.syncplay.utils.getFileName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMedia.Track
import org.videolan.libvlc.util.VLCVideoLayout
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_selected_sub
import syncplaymobile.shared.generated.resources.room_selected_sub_error
import syncplaymobile.shared.generated.resources.room_selected_vid
import syncplaymobile.shared.generated.resources.room_sub_error_load_vid_first
import java.io.IOException
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class VlcPlayer(viewmodel: SyncplayViewmodel) : BasePlayer(viewmodel, AndroidPlayerEngine.VLC) {
    lateinit var audioManager: AudioManager

    private lateinit var ctx: Context

    private var libvlc: LibVLC? = null
    private var vlcPlayer: MediaPlayer? = null
    private lateinit var vlcView: VLCVideoLayout

    private var vlcMedia: Media? = null

    override val canChangeAspectRatio: Boolean
        get() = true

    override val supportsChapters: Boolean
        get() = true

    override val trackerJobInterval: Duration = 250.milliseconds

    override fun initialize() {
        ctx = vlcView.context.applicationContext
        audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        libvlc = LibVLC(ctx, listOf("-vv"))
        vlcPlayer = MediaPlayer(libvlc)
        vlcPlayer?.attachViews(vlcView, null, true, false)

        vlcAttachObserver()
        startTrackingProgress()
    }

    override fun destroy() {
        vlcPlayer?.stop()
        vlcMedia?.release()
        vlcPlayer?.release()
        libvlc?.release()
    }

    @Composable
    override fun VideoPlayer(modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                vlcView = VlcviewBinding.inflate(LayoutInflater.from(context)).vlcview
                initialize()
                return@AndroidView vlcView
            },
            onReset = {
                try {
                    vlcPlayer?.attachViews(vlcView, null, true, false)
                } catch (_: Exception) {
                }
            },
            onRelease = {
                vlcPlayer?.detachViews()
            },
            update = {
                try {
                    vlcPlayer?.attachViews(vlcView, null, true, false)
                } catch (_: Exception) {
                }
            }
        )
    }

    //TODO
    override fun configurableSettings() = null

    override fun hasMedia(): Boolean {
        return vlcPlayer?.hasMedia() == true
    }

    override fun isPlaying(): Boolean {
        return vlcPlayer?.isPlaying == true
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        viewmodel.media?.subtitleTracks?.clear()
        viewmodel.media?.audioTracks?.clear()
        val audioTracks = vlcPlayer?.getTracks(Track.Type.Audio)
        audioTracks?.forEachIndexed { i, tracky ->
            viewmodel.media?.audioTracks?.add(
                object : VlcTrack {
                    override val name = tracky.name
                    override val type = TRACKTYPE.AUDIO
                    override val index = i
                    override val id = tracky.id
                    override val selected = mutableStateOf(tracky.selected)
                }
            )
        }

        val subtitleTracks = vlcPlayer?.getTracks(Track.Type.Text)
        subtitleTracks?.forEachIndexed { i, tracky ->
            viewmodel.media?.subtitleTracks?.add(
                object : VlcTrack {
                    override val name = tracky.name
                    override val type = TRACKTYPE.SUBTITLE
                    override val index = i
                    override val id = tracky.id
                    override val selected = mutableStateOf(tracky.selected)
                }
            )
        }
    }

    override fun selectTrack(track: com.yuroyami.syncplay.models.Track?, type: TRACKTYPE) {
        val vlcTrack = track as? VlcTrack

        when (type) {
            TRACKTYPE.SUBTITLE -> {
                if (vlcTrack != null) {
                    vlcPlayer?.selectTrack(vlcTrack.id)
                } else {
                    vlcPlayer?.unselectTrackType(Track.Type.Text)
                }

                viewmodel.playerManager.currentTrackChoices.subtitleSelectionIdVlc = vlcTrack?.id ?: "-1"
            }

            TRACKTYPE.AUDIO -> {
                if (vlcTrack != null) {
                    vlcPlayer?.selectTrack(vlcTrack.id)
                } else {
                    vlcPlayer?.unselectTrackType(Track.Type.Audio)
                }

                viewmodel.playerManager.currentTrackChoices.audioSelectionIdVlc = vlcTrack?.id ?: "-1"
            }
        }
    }

    override suspend fun analyzeChapters(mediafile: MediaFile) {
        mediafile.chapters.clear()
        val chapters = vlcPlayer?.getChapters(-1)
        chapters?.forEachIndexed { i, chptr ->
            mediafile.chapters.add(
                Chapter(
                    index = i,
                    name = chptr.name,
                    timestamp = chptr.timeOffset
                )
            )
        }
    }

    override fun jumpToChapter(chapter: Chapter) {
        vlcPlayer?.chapter = chapter.index
    }

    override fun skipChapter() {
        vlcPlayer?.nextChapter()
    }

    override fun reapplyTrackChoices() {
        val subId = viewmodel.playerManager.currentTrackChoices.subtitleSelectionIdVlc
        val audioId = viewmodel.playerManager.currentTrackChoices.audioSelectionIdVlc

        val ccMap = viewmodel.media?.subtitleTracks?.map { it as VlcTrack }
        val audioMap = viewmodel.media?.subtitleTracks?.map { it as VlcTrack }

        val ccGet = ccMap?.firstOrNull { it.id == subId }
        val audioGet = audioMap?.firstOrNull { it.id == audioId }

        with(viewmodel.player ?: return) {
            if (subId == "-1") {
                selectTrack(null, TRACKTYPE.SUBTITLE)
            } else if (ccGet != null) {
                selectTrack(ccGet, TRACKTYPE.SUBTITLE)
            }

            if (audioId == "-1") {
                selectTrack(null, TRACKTYPE.AUDIO)
            } else if (audioGet != null) {
                selectTrack(audioGet, TRACKTYPE.AUDIO)
            }
        }
    }

    override fun loadExternalSubImpl(uri: String, extension: String) {
        vlcPlayer?.addSlave(
            IMedia.Slave.Type.Subtitle, uri, true
        )
        //todo: catch specific error
    }

    override suspend fun injectVideoImpl(media: MediaFile, isUrl: Boolean) {
        delay(500)
        media.uri?.toUri()?.let { uri ->
            if (isUrl) {
                vlcPlayer?.play(uri)
            } else {
                val desc = ctx.contentResolver.openFileDescriptor(uri, "r")
                val media = Media(libvlc, desc?.fileDescriptor)
                //todo: global property to switch hw/sw
                vlcPlayer?.play(media)
            }
        }
    }

    override fun pause() {
        playerScopeMain.launch {
            vlcPlayer?.pause()
        }
    }

    override fun play() {
        playerScopeMain.launch {
            vlcPlayer?.play()
        }
    }

    override fun isSeekable(): Boolean {
        return vlcPlayer?.isSeekable == true
    }

    override fun seekTo(toPositionMs: Long) {
        super.seekTo(toPositionMs)
        vlcPlayer?.setTime(toPositionMs, true)
    }

    override fun currentPositionMs(): Long {
        return vlcPlayer?.time ?: 0L
    }

    override suspend fun switchAspectRatio(): String {
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

    override fun collectInfoLocal(mediafile: MediaFile) {
        collectInfoLocalAndroid(mediafile, ctx)
    }

    override fun changeSubtitleSize(newSize: Int) {
        //TODO
    }

    /** VLC EXCLUSIVE */
    private fun vlcAttachObserver() {
        vlcPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    if (vlcPlayer?.hasMedia() == true) {
                        viewmodel.playerManager.isNowPlaying.value = true //Just to inform UI

                        //Tell server about playback state change
                        if (!viewmodel.isSoloMode) {
                            viewmodel.actionManager.sendPlayback(true)
                        }
                    }
                }

                MediaPlayer.Event.Paused -> {
                    if (vlcPlayer?.hasMedia() == true) {
                        viewmodel.playerManager.isNowPlaying.value = false //Just to inform UI

                        //Tell server about playback state change
                        if (!viewmodel.isSoloMode) {
                            viewmodel.actionManager.sendPlayback(false)
                        }
                    }
                }

                MediaPlayer.Event.EndReached -> {
                    pause()
                    onPlaybackEnded()
                }

                MediaPlayer.Event.LengthChanged -> {
                    if (vlcPlayer?.hasMedia() == true) {
                        /* Updating our timeFull */
                        val durationMs = vlcPlayer!!.length

                        viewmodel.playerManager.timeFullMillis.value = abs(durationMs)

                        if (viewmodel.isSoloMode) return@setEventListener

                        if (durationMs / 1000.0 != viewmodel.media?.fileDuration) {
                            playerScopeIO.launch launch2@{
                                viewmodel.media?.fileDuration = durationMs / 1000.0
                                viewmodel.networkManager.send<PacketCreator.File> {
                                    media = viewmodel.media
                                }.await()
                            }
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

    fun toggleHW(enable: Boolean) {
        vlcMedia?.setHWDecoderEnabled(enable, true)
    }

    interface VlcTrack : com.yuroyami.syncplay.models.Track {
        val id: String
    }
}