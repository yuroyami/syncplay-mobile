package com.yuroyami.syncplay.player.vlc

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import cafe.adriel.lyricist.Lyricist
import com.yuroyami.syncplay.databinding.VlcviewBinding
import com.yuroyami.syncplay.lyricist.Stringies
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.PlayerUtils.trackProgress
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.utils.RoomUtils
import com.yuroyami.syncplay.utils.RoomUtils.checkFileMismatches
import com.yuroyami.syncplay.utils.collectInfoLocalAndroid
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.watchroom.dispatchOSD
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.lyricist
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMedia.Track
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.IOException
import kotlin.math.abs

class VlcPlayer : BasePlayer() {
    private lateinit var ctx: Context

    override val engine = ENGINE.ANDROID_VLC

    private var libvlc: LibVLC? = null
    private var vlcPlayer: MediaPlayer? = null
    private lateinit var vlcView: VLCVideoLayout

    private var vlcMedia: Media? = null

    override val canChangeAspectRatio: Boolean
        get() = true

    override val supportsChapters: Boolean
        get() = true

    override fun initialize() {
        ctx = vlcView.context.applicationContext
        libvlc = LibVLC(ctx, listOf("-vv"))
        vlcPlayer = MediaPlayer(libvlc)
        vlcPlayer?.attachViews(vlcView, null, true, false)

        vlcAttachObserver()
        playerScopeMain.trackProgress(intervalMillis = 250L)
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

    override fun hasMedia(): Boolean {
        return vlcPlayer?.hasMedia() == true
    }

    override fun isPlaying(): Boolean {
        return vlcPlayer?.isPlaying == true
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        viewmodel?.media?.subtitleTracks?.clear()
        viewmodel?.media?.audioTracks?.clear()
        val audioTracks = vlcPlayer?.getTracks(Track.Type.Audio)
        audioTracks?.forEachIndexed { i, tracky ->
            viewmodel?.media?.audioTracks?.add(
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
            viewmodel?.media?.subtitleTracks?.add(
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

                viewmodel?.currentTrackChoices?.subtitleSelectionIdVlc = vlcTrack?.id ?: "-1"
            }

            TRACKTYPE.AUDIO -> {
                if (vlcTrack != null) {
                    vlcPlayer?.selectTrack(vlcTrack.id)
                } else {
                    vlcPlayer?.unselectTrackType(Track.Type.Audio)
                }

                viewmodel?.currentTrackChoices?.audioSelectionIdVlc = vlcTrack?.id ?: "-1"
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
        val subId = viewmodel?.currentTrackChoices?.subtitleSelectionIdVlc
        val audioId = viewmodel?.currentTrackChoices?.audioSelectionIdVlc

        val ccMap = viewmodel?.media?.subtitleTracks?.map { it as VlcTrack }
        val audioMap = viewmodel?.media?.subtitleTracks?.map { it as VlcTrack }

        val ccGet = ccMap?.firstOrNull { it.id == subId }
        val audioGet = audioMap?.firstOrNull { it.id == audioId }

        with(viewmodel?.player ?: return) {
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

    override fun loadExternalSub(uri: String) {
        if (hasMedia()) {
            val filename = getFileName(uri = uri).toString()
            val extension = filename.substring(filename.length - 4)

            val mimeType =
                if (extension.contains("srt")) MimeTypes.APPLICATION_SUBRIP
                else if ((extension.contains("ass"))
                    || (extension.contains("ssa"))
                ) MimeTypes.TEXT_SSA
                else if (extension.contains("ttml")) MimeTypes.APPLICATION_TTML
                else if (extension.contains("vtt")) MimeTypes.TEXT_VTT else ""

            if (mimeType != "") {
                vlcPlayer?.addSlave(
                    IMedia.Slave.Type.Subtitle, uri, true
                ) //todo: catch error

                playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedSub(filename))
            } else {
                playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedSubError)
            }
        } else {
            playerScopeMain.dispatchOSD(lyricist.strings.roomSubErrorLoadVidFirst)
        }
    }

    @SuppressLint("Recycle")
    override fun injectVideo(uri: String?, isUrl: Boolean) {
        /* Changing UI (hiding artwork, showing media controls) */
        viewmodel?.hasVideoG?.value = true

        playerScopeMain.launch {
            /* Creating a media file from the selected file */
            if (uri != null || viewmodel?.media == null) {
                viewmodel?.media = MediaFile()
                viewmodel?.media?.uri = uri

                /* Obtaining info from it (size and name) */
                if (isUrl) {
                    viewmodel?.media?.url = uri.toString()
                    viewmodel?.media?.let { collectInfoURL(it) }
                } else {
                    viewmodel?.media?.let { collectInfoLocal(it) }
                }
            }
            /* Injecting the media into exoplayer */
            try {

                delay(500)
                uri?.toUri()?.let {
                    if (isUrl) {
                        vlcPlayer?.play(it)
                    } else {
                        val desc = ctx.contentResolver.openFileDescriptor(it, "r")
                        val media = Media(libvlc, desc?.fileDescriptor) //todo: global property to switch hw/sw
                        vlcPlayer?.play(media)
                    }
                }

                /* Goes back to the beginning for everyone */
                if (!isSoloMode) {
                    viewmodel!!.p.currentVideoPosition = 0.0
                }
            } catch (e: IOException) {
                /* If, for some reason, the video didn't wanna load */
                e.printStackTrace()
                playerScopeMain.dispatchOSD("There was a problem loading this file.")
            }

            /* Finally, show a a toast to the user that the media file has been added */
            val lyricist = Lyricist("en", Stringies)
            playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedVid("${viewmodel?.media?.fileName}"))

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

    override fun switchAspectRatio(): String {
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
                        viewmodel?.isNowPlaying?.value = true //Just to inform UI

                        //Tell server about playback state change
                        if (!isSoloMode) {
                            RoomUtils.sendPlayback(true)
                            viewmodel!!.p.paused = false
                        }
                    }
                }

                MediaPlayer.Event.Paused -> {
                    if (vlcPlayer?.hasMedia() == true) {
                        viewmodel?.isNowPlaying?.value = false //Just to inform UI

                        //Tell server about playback state change
                        if (!isSoloMode) {
                            RoomUtils.sendPlayback(false)
                            viewmodel!!.p.paused = true
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
                        val duration = vlcPlayer!!.length.div(1000.0)

                        viewmodel?.timeFull?.longValue = abs(duration.toLong())

                        if (isSoloMode) return@setEventListener

                        if (duration != viewmodel?.media?.fileDuration) {
                            playerScopeIO.launch launch2@{
                                viewmodel?.media?.fileDuration = duration
                                viewmodel!!.p.sendPacket(JsonSender.sendFile(viewmodel?.media ?: return@launch2))
                            }
                        }
                    }
                }
            }
        }

    }


    fun toggleHW(enable: Boolean) {
        vlcMedia?.setHWDecoderEnabled(enable, true)
    }

    interface VlcTrack : com.yuroyami.syncplay.models.Track {
        val id: String
    }
}