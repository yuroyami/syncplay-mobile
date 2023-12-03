package com.yuroyami.syncplay.player.avplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.ENGINE
import com.yuroyami.syncplay.watchroom.hasVideoG
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.media
import com.yuroyami.syncplay.watchroom.p
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.AVFoundation.seekableTimeRanges
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRect
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL.Companion.URLWithString
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView

class AvPlayer : BasePlayer() {

    val engine = ENGINE.IOS_AVPLAYER

    /*-- Exoplayer-related properties --*/
    var avplayer: AVPlayer? = null
    lateinit var avView:  AVPlayerViewController
    var playerlayer: AVPlayerLayer? = null
    private var currentMedia: AVPlayerItem? = null

    override fun initialize() {
        avplayer = AVPlayer.new()
        playerlayer = AVPlayerLayer()
        avView = AVPlayerViewController()
        avView.player = avplayer!!
        avView.showsPlaybackControls = false
        playerlayer?.player = avplayer
    }

    @OptIn(ExperimentalForeignApi::class)
    @Composable
    override fun VideoPlayer(modifier: Modifier) {
        UIKitView(
            modifier = modifier,
            factory = {
                initialize()
                val playerContainer = UIView()
                playerContainer.addSubview(avView.view)
                playerContainer
            },
            onResize = { view: UIView, rect: CValue<CGRect> ->
                CATransaction.begin()
                CATransaction.setValue(true, kCATransactionDisableActions)
                view.layer.setFrame(rect)
                playerlayer?.setFrame(rect)
                avView.view.layer.frame = rect
                CATransaction.commit()
            },
            update = { }
        )
    }

    override fun hasMedia(): Boolean {
        return avplayer?.currentItem != null
    }

    override fun isPlaying(): Boolean {
        return (avplayer?.rate() ?: 0f) > 0f && avplayer?.error == null
    }

    override fun analyzeTracks(mediafile: MediaFile) {
        TODO("Not yet implemented")
    }

    override fun selectTrack(type: Int, index: Int) {
        TODO("Not yet implemented")
    }

    override fun reapplyTrackChoices() {
        TODO("Not yet implemented")
    }

    override fun loadExternalSub(uri: String) {
        TODO("Not yet implemented")
    }

    override fun injectVideo(uri: String?, isUrl: Boolean) {
        hasVideoG.value = true

        playerScopeMain.launch {
            /* Creating a media file from the selected file */
            if (uri != null || media == null) {
                media = MediaFile()
                media?.uri = uri

                /* Obtaining info from it (size and name) */
                if (isUrl) {
                    media?.url = uri.toString()
                    //TODO: media?.collectInfoURL()
                } else {
                    //TODO: media?.collectInfo(applicationContext)
                }

                /* Checking mismatches with others in room */
                //checkFileMismatches(p) TODO
            }
            /* Injecting the media into avplayer */
            try {
                uri?.let {
                    currentMedia = AVPlayerItem(uRL = URLWithString(it)!!)
                    avplayer = AVPlayer.playerWithPlayerItem(currentMedia)
                }

                /* Goes back to the beginning for everyone */
                if (!isSoloMode) {
                    p.currentVideoPosition = 0.0
                }

                /* Seeing if we have to start over TODO **/
                //if (startFromPosition != (-3.0).toLong()) myExoPlayer?.seekTo(startFromPosition)
            } catch (e: Exception) {
                /* If, for some reason, the video didn't wanna load */
                e.printStackTrace()
                //TODO: toasty("There was a problem loading this file.")
            }

            /* Finally, show a a toast to the user that the media file has been added */
            //TODO: toasty(string(R.string.room_selected_vid, "${media?.fileName}"))
        }
    }

    override fun pause() {
        avplayer?.pause()
    }

    override fun play() {
        avplayer?.play()
    }

    override fun isSeekable(): Boolean {
        return avplayer?.currentItem?.seekableTimeRanges?.isNotEmpty() == true
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun seekTo(toPositionMs: Long) {
        avplayer?.seekToTime(CMTimeMake(toPositionMs, 1000))
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun currentPositionMs(): Long {
        return avplayer?.currentTime()?.useContents {
            this.value
        } ?: 0L
    }

    override fun switchAspectRatio(): String {
        TODO("Not yet implemented")
    }
}
