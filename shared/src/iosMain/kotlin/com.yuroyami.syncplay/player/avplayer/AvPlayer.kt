package com.yuroyami.syncplay.player.avplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.PlayerUtils.trackProgress
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.utils.collectInfoLocaliOS
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.screens.room.dispatchOSD
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import platform.AVFoundation.AVLayerVideoGravityResize
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaCharacteristic
import platform.AVFoundation.AVMediaSelectionGroup
import platform.AVFoundation.AVMediaSelectionOption
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeText
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.asset
import platform.AVFoundation.availableMediaCharacteristicsWithMediaSelectionOptions
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.mediaSelectionGroupForMediaCharacteristic
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.AVFoundation.seekableTimeRanges
import platform.AVFoundation.selectMediaOption
import platform.AVFoundation.timeControlStatus
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRect
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSKeyValueObservingOptionNew
import platform.Foundation.NSURL
import platform.Foundation.NSURL.Companion.URLWithString
import platform.Foundation.NSURL.Companion.fileURLWithPath
import platform.Foundation.addObserver
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView
import platform.darwin.NSObject
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_selected_sub
import syncplaymobile.shared.generated.resources.room_selected_sub_error
import syncplaymobile.shared.generated.resources.room_selected_vid
import syncplaymobile.shared.generated.resources.room_sub_error_load_vid_first
import kotlin.math.roundToLong

class AvPlayer : BasePlayer() {

    override val engine = ENGINE.IOS_AVPLAYER

    /*-- Exoplayer-related properties --*/
    var avPlayer: AVPlayer? = null
    private lateinit var avView: AVPlayerViewController
    var avPlayerLayer: AVPlayerLayer? = null
    private var avContainer: UIView? = null

    private var avMedia: AVPlayerItem? = null

    override val canChangeAspectRatio: Boolean
        get() = true

    override fun initialize() {
        //avView.view.setBackgroundColor(UIColor.clearColor())
        //avPlayerLayer!!.setBackgroundColor(UIColor.clearColor().CGColor)
        avView.showsPlaybackControls = false
        playerScopeMain.trackProgress(intervalMillis = 250L)
    }

    private fun reassign() {
        avView.player = avPlayer!!
        avPlayerLayer?.player = avPlayer

        avPlayer?.addObserver(
            observer = object: NSObject() {
                //todo
            },
            forKeyPath = "timeControlStatus",
            options = NSKeyValueObservingOptionNew,
            context = null
        )

        val status = avPlayer?.timeControlStatus()
    }

    override fun destroy() {
        avPlayer?.pause()
        avPlayer?.finalize()
        avPlayer = null
        avContainer = null
    }

    @OptIn(ExperimentalForeignApi::class)
    @Composable
    override fun VideoPlayer(modifier: Modifier) {
        UIKitView(
            modifier = modifier,
            factory = remember {
                factorylambda@{
                    avPlayerLayer = AVPlayerLayer()
                    avView = AVPlayerViewController()
                    initialize()
                    avContainer = UIView()
                    avContainer!!.addSubview(avView.view)
                    return@factorylambda avContainer!!
                }
            },
            onResize = { view: UIView, rect: CValue<CGRect> ->
                CATransaction.begin()
                CATransaction.setValue(true, kCATransactionDisableActions)
                view.layer.setFrame(rect)
                avPlayerLayer?.setFrame(rect)
                avView.view.layer.frame = rect
                CATransaction.commit()
            },
            update = { }
        )
    }

    override fun hasMedia(): Boolean {
        return avPlayer?.currentItem != null
    }

    override fun isPlaying(): Boolean {
        return (avPlayer?.rate() ?: 0f) > 0f && avPlayer?.error == null
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        // Check if the AVPlayer is initialized
        if (avPlayer == null || avMedia == null) return

        viewmodel?.media?.subtitleTracks?.clear()
        viewmodel?.media?.audioTracks?.clear()

        //Groups
        val asset = avPlayer?.currentItem?.asset ?: return
        val characteristics = asset.availableMediaCharacteristicsWithMediaSelectionOptions.map { it as AVMediaCharacteristic }
        characteristics.forEach {
            loggy("Characteristic: $it")

            val group = asset.mediaSelectionGroupForMediaCharacteristic(it)
            group?.options?.map { op -> op as AVMediaSelectionOption }?.forEachIndexed { i, option ->
                loggy("Option: $option")

                val isSelected = group.defaultOption == option

                if (option.mediaType == AVMediaTypeText || option.mediaType == AVMediaTypeAudio) {
                    mediafile.audioTracks.add(
                        object : AvTrack {
                            override val sOption = option
                            override val sGroup: AVMediaSelectionGroup = group
                            override val name = option.displayName + " [${option.extendedLanguageTag}]"
                            override val index = i
                            override val type = if (option.mediaType == AVMediaTypeAudio) TRACKTYPE.AUDIO else TRACKTYPE.SUBTITLE
                            override val selected = mutableStateOf(isSelected)
                        }
                    )
                }
            }
        }
//
//
//        // Extract audio tracks information
//        val audioTracks = avMedia!!.tracks.map { it as AVAssetTrack }.filter { it.mediaType == AVMediaTypeAudio }
//        audioTracks.forEachIndexed { i, track ->
//            mediafile.audioTracks.add(
//                object : Track {
//                    override val name = track.languageCode ?: "Track ${i + 1}"
//                    override val index = i
//                    override val type = TRACKTYPE.AUDIO
//                    override val selected = mutableStateOf(track.selected)
//                }
//            )
//        }
//
//        val subtitleTracks = avMedia!!.tracks.map { it as AVAssetTrack }.filter { it.mediaType == AVMediaTypeText }
//        subtitleTracks.forEachIndexed { i, track ->
//            mediafile.subtitleTracks.add(
//                object : Track {
//                    override val name = track.languageCode ?: "Subtitle ${i + 1}"
//                    override val index = i
//                    override val type = TRACKTYPE.SUBTITLE
//                    override val selected = mutableStateOf(track.selected)
//                }
//            )
//        }
    }

    override fun selectTrack(track: Track?, type: TRACKTYPE) {
        val avtrack = track as? AvTrack

        if (avtrack != null) {
            avPlayer?.currentItem?.selectMediaOption(avtrack.sOption, avtrack.sGroup)
        } else {
            val asset = avPlayer?.currentItem?.asset ?: return
            val characteristics = asset.availableMediaCharacteristicsWithMediaSelectionOptions.map { it as AVMediaCharacteristic }
            var groupInQuestion: AVMediaSelectionGroup? = null
            characteristics.forEach { characteristic ->
                val group = asset.mediaSelectionGroupForMediaCharacteristic(characteristic)

                when (type) {
                    TRACKTYPE.AUDIO -> {
                        val isAudio = group?.options?.any { (it as? AVMediaSelectionOption)?.mediaType == AVMediaTypeAudio }
                        if (isAudio == true) {
                            groupInQuestion = group
                        }
                    }
                    TRACKTYPE.SUBTITLE -> {
                        val isSubtitle = group?.options?.any { (it as? AVMediaSelectionOption)?.mediaType == AVMediaTypeText }
                        if (isSubtitle == true) {
                            groupInQuestion = group
                        }
                    }
                }
            }

            if (groupInQuestion?.allowsEmptySelection == true) {
                avPlayer?.currentItem?.selectMediaOption(null, groupInQuestion!!)
            }
        }
    }

    override fun reapplyTrackChoices() {
        //TODO("Not yet implemented")
    }

    override fun loadExternalSub(uri: String) {
        if (hasMedia()) {
            val filename = getFileName(uri = uri).toString()
            val extension = filename.substring(filename.lastIndexOf('.') + 1).lowercase()

            val mimeTypeValid = listOf("srt", "ass", "ssa", "ttml", "vtt").contains(extension)

            if (mimeTypeValid) {
                val subUri = NSURL.URLWithString(uri)!!

                //TODO: Doesn't support loading external subs
                playerScopeMain.dispatchOSD {
                    getString(Res.string.room_selected_sub, filename)
                }
            } else {
                playerScopeMain.dispatchOSD {
                    getString(Res.string.room_selected_sub_error)
                }            }
        } else {
            playerScopeMain.dispatchOSD {
                getString(Res.string.room_sub_error_load_vid_first)
            }
        }
    }

    override fun injectVideo(uri: String?, isUrl: Boolean) {
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
            /* Injecting the media into avplayer */
            try {
                delay(500)
                uri?.let {
                    avMedia = if (isUrl) {
                        val nsurl = URLWithString(it) ?: throw Exception()
                        val avmedia = AVPlayerItem(uRL = nsurl)
                        avPlayer = AVPlayer.playerWithPlayerItem(avmedia)
                        avMedia
                    } else {
                        val nsurl = fileURLWithPath(it)
                        val avmedia = AVPlayerItem(nsurl)
                        avPlayer = AVPlayer.playerWithPlayerItem(avmedia)
                        avmedia
                    }


                    reassign()

                    val isTimeout = withTimeoutOrNull(5000) {
                        while (avMedia?.status != AVPlayerItemStatusReadyToPlay) {
                            delay(250)
                        }

                        val duration = avMedia!!.asset.duration.toMillis().div(1000.0)

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

                    if (isTimeout == null) throw Exception("Media not loaded by AVPlayer")
                }

                /* Goes back to the beginning for everyone */
                if (!isSoloMode) {
                    viewmodel?.p?.currentVideoPosition = 0.0
                }
            } catch (e: Exception) {
                /* If, for some reason, the video didn't wanna load */
                e.printStackTrace()
                playerScopeMain.dispatchOSD("There was a problem loading this file.")
            }

            /* Finally, show a a toast to the user that the media file has been added */
            playerScopeMain.dispatchOSD {
                getString(Res.string.room_selected_vid,"${viewmodel?.media?.fileName}")
            }
        }
    }

    override fun pause() {
        avPlayer?.pause()
    }

    override fun play() {
        avPlayer?.play()
    }

    override fun isSeekable(): Boolean {
        return avPlayer?.currentItem?.seekableTimeRanges?.isNotEmpty() == true
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun seekTo(toPositionMs: Long) {
        super.seekTo(toPositionMs)
        avPlayer?.seekToTime(CMTimeMake(toPositionMs, 1000))
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun currentPositionMs(): Long {
        return avPlayer?.currentTime()?.toMillis() ?: 0L
    }

    override suspend fun switchAspectRatio(): String {
        val scales = listOf(
            AVLayerVideoGravityResize,
            AVLayerVideoGravityResizeAspect,
            AVLayerVideoGravityResizeAspectFill
        )
        val current = avPlayerLayer?.videoGravity
        val currentIndex = if (current != null) scales.indexOf(current) else -1
        val nextIndex = (currentIndex + 1) % scales.size
        val nextScale = scales[nextIndex]
        avPlayerLayer?.videoGravity = nextScale
        return nextScale!!
    }

    override fun collectInfoLocal(mediafile: MediaFile) {
        collectInfoLocaliOS(mediafile)
    }

    override fun changeSubtitleSize(newSize: Int) {

    }

    override val supportsChapters = false
    override suspend fun analyzeChapters(mediafile: MediaFile) = Unit
    override fun jumpToChapter(chapter: Chapter) = Unit
    override fun skipChapter() = Unit


    private fun CValue<CMTime>.toMillis(): Long {
        return CMTimeGetSeconds(this).times(1000.0).roundToLong()
    }

    interface AvTrack : Track {
        val sOption: AVMediaSelectionOption
        val sGroup: AVMediaSelectionGroup
    }
}
