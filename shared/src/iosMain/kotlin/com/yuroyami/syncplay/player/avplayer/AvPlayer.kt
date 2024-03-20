package com.yuroyami.syncplay.player.avplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import cafe.adriel.lyricist.Lyricist
import com.yuroyami.syncplay.lyricist.Stringies
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.PlayerUtils.trackProgress
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.utils.RoomUtils.checkFileMismatches
import com.yuroyami.syncplay.utils.collectInfoLocaliOS
import com.yuroyami.syncplay.watchroom.dispatchOSD
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.asset
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.AVFoundation.seekableTimeRanges
import platform.AVKit.AVPlayerViewController
import platform.AVKit.AVPlayerViewControllerDelegateProtocol
import platform.CoreGraphics.CGRect
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL.Companion.URLWithString
import platform.Foundation.NSURL.Companion.fileURLWithPath
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView
import platform.darwin.NSObject
import kotlin.math.roundToLong

class AvPlayer : BasePlayer() {

    override val engine = ENGINE.IOS_AVPLAYER

    /*-- Exoplayer-related properties --*/
    private var avPlayer: AVPlayer? = null
    private lateinit var avView: AVPlayerViewController
    private var avPlayerLayer: AVPlayerLayer? = null
    private var avContainer: UIView? = null

    private var avMedia: AVPlayerItem? = null

    override val canChangeAspectRatio: Boolean
        get() = true

    override fun initialize() {
        //avView.view.setBackgroundColor(UIColor.clearColor())
        //avPlayerLayer!!.setBackgroundColor(UIColor.clearColor().CGColor)
        avView.showsPlaybackControls = false
        avView.delegate = object: NSObject(), AVPlayerViewControllerDelegateProtocol {

        }
        playerScopeMain.trackProgress(intervalMillis = 250L)
    }

    fun reassign() {
        avView.player = avPlayer!!
        avPlayerLayer?.player = avPlayer
    }

    @OptIn(ExperimentalForeignApi::class)
    @Composable
    override fun VideoPlayer(modifier: Modifier) {
        UIKitView(
            modifier = modifier,
            factory = remember {
                factorylambda@ {
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

    override fun analyzeTracks(mediafile: MediaFile) {
        //TODO("Not yet implemented")
    }

    override fun selectTrack(type: TRACKTYPE, index: Int) {
        //TODO("Not yet implemented")
    }

    override fun reapplyTrackChoices() {
        //TODO("Not yet implemented")
    }

    override fun loadExternalSub(uri: String) {
        //TODO("Not yet implemented")
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

                /* Checking mismatches with others in room */
                checkFileMismatches()
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
            val lyricist = Lyricist("en", Stringies)
            playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedVid("${viewmodel?.media?.fileName}"))
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
        avPlayer?.seekToTime(CMTimeMake(toPositionMs, 1000))
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun currentPositionMs(): Long {
        return avPlayer?.currentTime()?.toMillis() ?: 0L
    }

    override fun switchAspectRatio(): String {
        //TODO
        return ""
    }

    override fun collectInfoLocal(mediafile: MediaFile) {
        collectInfoLocaliOS(mediafile)
    }

    override fun changeSubtitleSize(newSize: Int) {

    }

    override val supportsChapters = false
    override fun analyzeChapters(mediafile: MediaFile) = Unit
    override fun jumpToChapter(chapter: Chapter) = Unit
    override fun skipChapter() = Unit


    fun CValue<CMTime>.toMillis(): Long {
        return CMTimeGetSeconds(this).times(1000.0).roundToLong()
    }
}
