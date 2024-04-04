package com.yuroyami.syncplay.player.vlc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import cafe.adriel.lyricist.Lyricist
import cocoapods.MobileVLCKit.VLCLibrary
import cocoapods.MobileVLCKit.VLCMedia
import cocoapods.MobileVLCKit.VLCMediaPlaybackSlaveTypeSubtitle
import cocoapods.MobileVLCKit.VLCMediaPlayer
import cocoapods.MobileVLCKit.VLCMediaPlayerDelegateProtocol
import cocoapods.MobileVLCKit.VLCMediaPlayerState
import cocoapods.MobileVLCKit.VLCTime
import com.yuroyami.syncplay.lyricist.Stringies
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.PlayerUtils.trackProgress
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.utils.RoomUtils
import com.yuroyami.syncplay.utils.RoomUtils.checkFileMismatches
import com.yuroyami.syncplay.utils.collectInfoLocaliOS
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.watchroom.dispatchOSD
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.lyricist
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayerLayer
import platform.Foundation.NSArray
import platform.Foundation.NSNotification
import platform.Foundation.NSNumber
import platform.Foundation.NSURL.Companion.URLWithString
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject

class VlcPlayer : BasePlayer() {
    override val engine = ENGINE.IOS_VLC

    private var libvlc: VLCLibrary? = null
    var vlcPlayer: VLCMediaPlayer? = null
    private var vlcView: UIView? = null

    private var vlcMedia: VLCMedia? = null

    var pipLayer: AVPlayerLayer? = null

    override val canChangeAspectRatio: Boolean
        get() = false //todo

    override val supportsChapters: Boolean
        get() = true


    override fun destroy() {
        vlcPlayer?.stop()
        vlcPlayer?.finalize()
        vlcPlayer = null
        vlcMedia = null
        libvlc?.finalize()
        libvlc = null
    }

    @Composable
    override fun VideoPlayer(modifier: Modifier) {
        UIKitView(
            modifier = modifier,
            factory = {
                vlcView = UIView()
                vlcView!!.setBackgroundColor(UIColor.clearColor())
                libvlc = VLCLibrary(listOf("-vv"))
                vlcPlayer = VLCMediaPlayer(libvlc!!)
                vlcPlayer!!.drawable = vlcView

                /* Layer for PIP */
                pipLayer = AVPlayerLayer.playerLayerWithPlayer(null)
                pipLayer?.frame = vlcView!!.bounds
                pipLayer?.videoGravity = AVLayerVideoGravityResizeAspect
                pipLayer?.addSublayer(vlcView!!.layer)

                return@UIKitView vlcView!!.also {
                    initialize()
                }
            },
            interactive = false,
            update = { }
        )
    }

    override fun initialize() {
        vlcPlayer!!.delegate = object: NSObject(), VLCMediaPlayerDelegateProtocol {

            override fun mediaPlayerStateChanged(aNotification: NSNotification) {
                if (hasMedia()) {
                    val isPlaying = vlcPlayer?.state != VLCMediaPlayerState.VLCMediaPlayerStatePaused
                    viewmodel?.isNowPlaying?.value = isPlaying //Just to inform UI

                    //Tell server about playback state change
                    if (!isSoloMode) {
                        RoomUtils.sendPlayback(isPlaying)
                        viewmodel!!.p.paused = !isPlaying
                    }
                }
            }
        }

        playerScopeIO.trackProgress(intervalMillis = 1000L)
    }

    override fun hasMedia(): Boolean {
        return vlcPlayer?.media != null
    }

    override fun isPlaying(): Boolean {
        return vlcPlayer?.isPlaying() == true
    }

    @Suppress("UNCHECKED_CAST")
    override fun analyzeTracks(mediafile: MediaFile) {
        if (vlcPlayer == null) return

        viewmodel?.media?.subtitleTracks?.clear()
        viewmodel?.media?.audioTracks?.clear()

        val audioTracks = vlcPlayer!!.audioTrackIndexes.zip(vlcPlayer!!.audioTrackNames).toMap()
        audioTracks.forEach { tracky ->
            val audioTrack = tracky as? Map.Entry<Int, String> ?: return@forEach

            viewmodel?.media?.audioTracks?.add(
                com.yuroyami.syncplay.models.Track(
                    name = audioTrack.value,
                    index = audioTrack.key,
                    trackType = TRACKTYPE.AUDIO
                ).apply {
                    selected.value = vlcPlayer!!.currentAudioTrackIndex == audioTrack.key
                }
            )
        }

        val subTracks = vlcPlayer!!.videoSubTitlesIndexes.zip(vlcPlayer!!.videoSubTitlesNames).toMap()
        subTracks.forEach { tracky ->
            val subTrack = tracky as? Map.Entry<Int, String> ?: return@forEach

            viewmodel?.media?.subtitleTracks?.add(
                com.yuroyami.syncplay.models.Track(
                    name = subTrack.value,
                    index = subTrack.key,
                    trackType = TRACKTYPE.AUDIO
                ).apply {
                    selected.value = vlcPlayer!!.currentVideoSubTitleIndex == subTrack.key
                }
            )
        }
    }

    override fun selectTrack(type: TRACKTYPE, index: Int) {
        when (type) {
            TRACKTYPE.SUBTITLE -> {
                if (index >= 0) {
                    vlcPlayer?.setCurrentVideoSubTitleIndex(viewmodel?.media?.subtitleTracks?.get(index)?.index!!)
                } else if (index == -1) {
                    vlcPlayer?.setCurrentVideoSubTitleIndex(-1)
                }

                viewmodel?.currentTrackChoices?.subtitleSelectionIndexVlc = index
            }

            TRACKTYPE.AUDIO -> {
                if (index >= 0) {
                    vlcPlayer?.setCurrentAudioTrackIndex(viewmodel?.media?.audioTracks?.get(index)?.index ?: 0)
                } else if (index == -1) {
                    vlcPlayer?.setCurrentAudioTrackIndex(-1)
                }

                viewmodel?.currentTrackChoices?.audioSelectionIndexVlc = index
            }
        }
    }

    override fun analyzeChapters(mediafile: MediaFile) {
        mediafile.chapters.clear()
        val chapters = (vlcPlayer?.chapterDescriptionsOfTitle(0) as? NSArray)?.toKList<String>()

        chapters?.forEachIndexed { i, chptr ->
            if (chptr.isNullOrBlank()) return@forEachIndexed

            mediafile.chapters.add(
                Chapter(
                    index = i,
                    name = chptr,
                    timestamp = 0 //todo
                )
            )
        }
    }

    override fun jumpToChapter(chapter: Chapter) {
        vlcPlayer?.setCurrentChapterIndex(chapter.index)
    }

    override fun skipChapter() {
        vlcPlayer?.nextChapter()
    }

    override fun reapplyTrackChoices() {

        val subIndex = viewmodel?.currentTrackChoices?.subtitleSelectionIndexMpv
        val audioIndex = viewmodel?.currentTrackChoices?.audioSelectionIndexMpv

        with(viewmodel?.player ?: return) {
            if (subIndex != null) selectTrack(TRACKTYPE.SUBTITLE, subIndex)
            if (audioIndex != null) selectTrack(TRACKTYPE.AUDIO, audioIndex)
        }
    }

    override fun loadExternalSub(uri: String) {
        if (hasMedia()) {
            val filename = getFileName(uri = uri).toString()
            val extension = filename.substring(filename.length - 4)

            val mimeTypeValid = extension.contains("srt")
                    || extension.contains("ass")
                    || extension.contains("ssa")
                    || extension.contains("ttml")
                    || extension.contains("vtt")

            if (mimeTypeValid) {
                vlcPlayer?.addPlaybackSlave(
                    URLWithString(uri)!!,
                    VLCMediaPlaybackSlaveTypeSubtitle,
                    true
                )

                playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedSub(filename))

            } else {
                playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedSubError)
            }
        } else {
            playerScopeMain.dispatchOSD(lyricist.strings.roomSubErrorLoadVidFirst)
        }
    }

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

                /* Checking mismatches with others in room */
                checkFileMismatches()
            }
            /* Injecting the media into exoplayer */
            try {

                delay(500)
                loggy("Attempting to insert media: $uri")

                uri?.let {
                    vlcMedia = if (isUrl) {
                        val nsurl = URLWithString(it) ?: throw Exception()
                        val vlcmedia = VLCMedia(uRL = nsurl)
                        vlcPlayer?.setMedia(vlcmedia)
                        vlcmedia
                    } else {
                        val vlcmedia = VLCMedia(path = it)
                        vlcPlayer?.setMedia(vlcmedia)
                        vlcmedia
                    }

                    vlcMedia?.synchronousParse()

                    val duration = vlcMedia!!.length.numberValue.doubleValue.div(1000.0)

                    viewmodel?.timeFull?.longValue = kotlin.math.abs(duration.toLong())

                    if (!isSoloMode) {
                        if (duration != viewmodel?.media?.fileDuration) {
                            playerScopeIO.launch launch2@{
                                viewmodel?.media?.fileDuration = duration
                                viewmodel!!.p.sendPacket(JsonSender.sendFile(viewmodel?.media ?: return@launch2))
                            }
                        }
                    }
                }

                /* Goes back to the beginning for everyone */
                if (!isSoloMode) {
                    viewmodel!!.p.currentVideoPosition = 0.0
                }
            } catch (e: Exception) {
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
        vlcPlayer?.pause()
    }

    override fun play() {
        vlcPlayer?.play()
    }

    override fun isSeekable(): Boolean {
        return vlcPlayer?.isSeekable() == true
    }

    override fun seekTo(toPositionMs: Long) {
        super.seekTo(toPositionMs)
        vlcPlayer?.setTime(toPositionMs.toVLCTime())
    }

    override fun currentPositionMs(): Long {
        return vlcPlayer?.time?.value()?.longValue ?: 0L
    }

    override fun switchAspectRatio(): String {
        //todo
//        val scaleTypes = VLCMediaPlayer.ScaleType.getMainScaleTypes()
//
//        val currentScale = vlcPlayer?.videoScale
//        val nextScaleIndex = scaleTypes.indexOf(currentScale) + 1
//        vlcPlayer?.videoScale = if (nextScaleIndex == scaleTypes.size) {
//            scaleTypes[0]
//        } else {
//            scaleTypes[nextScaleIndex]
//        }
//
//        return vlcPlayer?.videoScale?.name ?: "ORIGINAL"
        return ""
    }

    override fun collectInfoLocal(mediafile: MediaFile) {
        collectInfoLocaliOS(mediafile)
    }

    override fun changeSubtitleSize(newSize: Int) {
        //TODO
    }

    /** VLC EXCLUSIVE */
    /* Some ObjC-interop/VLC-interop helper methods */
    /** Converts a long to a VLCTime, to use with VLCMediaPlayer */
    private fun Long.toVLCTime(): VLCTime {
        return VLCTime(number = NSNumber(long = this))
    }

    /** Converts an NSArray to a valid Kotlin list, to use with VLC tracks */
    @Suppress("UNCHECKED_CAST")
    private fun <T> NSArray.toKList(): List<T?> {
        return List(count.toInt()) { index ->
            objectAtIndex(index.toULong()) as? T
        }
    }

//    companion object VLClistener : NSObject(), VLCMediaPlayerDelegateProtocol {
//
//        override fun mediaPlayerStateChanged(aNotification: NSNotification) {
//            if (hasMedia()) {
//                val isPlaying = vlcPlayer?.state != VLCMediaPlayerState.VLCMediaPlayerStatePaused
//                viewmodel?.isNowPlaying?.value = isPlaying //Just to inform UI
//
//                //Tell server about playback state change
//                if (!isSoloMode) {
//                    RoomUtils.sendPlayback(isPlaying)
//                    viewmodel!!.p.paused = !isPlaying
//                }
//            }
//        }

//        override fun mediaPlayerTitleChanged(aNotification: NSNotification) {
//            if (hasMedia()) {
//                /* Updating our timeFull */
//                val duration = vlcPlayer?.media?.length?.numberValue?.doubleValue?.div(1000.0) ?: 0.0
//
//                viewmodel?.timeFull?.longValue = kotlin.math.abs(duration.toLong())
//
//                if (isSoloMode) return
//
//                if (duration != viewmodel?.media?.fileDuration) {
//                    playerScopeIO.launch launch2@{
//                        viewmodel?.media?.fileDuration = duration
//                        viewmodel!!.p.sendPacket(JsonSender.sendFile(viewmodel?.media ?: return@launch2))
//                    }
//                }
//            }
//        }
//        override fun mediaPlayerLengthChanged(length: int64_t) {
//            if (hasMedia()) {
//                /* Updating our timeFull */
//                val duration = length.div(1000.0)
//
//                viewmodel?.timeFull?.longValue = abs(duration.toLong())
//
//                if (isSoloMode) return
//
//                if (duration != viewmodel?.media?.fileDuration) {
//                    playerScopeIO.launch launch2@{
//                        viewmodel?.media?.fileDuration = duration
//                        viewmodel!!.p.sendPacket(JsonSender.sendFile(viewmodel?.media ?: return@launch2))
//                    }
//                }
//            }
//        }
//}
}