package com.yuroyami.syncplay.player.avplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.ENGINE
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRect
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView

class AVPlayer : BasePlayer {

    val engine = ENGINE.IOS_AVPLAYER

    /*-- Exoplayer-related properties --*/
    var avplayer: AVPlayer? = null
    private var currentMedia: AVPlayerItem? = null
    lateinit var avView: UIView

    val avMainScope = CoroutineScope(Dispatchers.Main)
    val avBGScope = CoroutineScope(Dispatchers.IO)

    override fun initialize() {

    }

    @OptIn(ExperimentalForeignApi::class)
    @Composable
    override fun VideoPlayer(modifier: Modifier) {
        val player = remember { AVPlayer(uRL = NSURL.new()!!) }
        val playerLayer = remember { AVPlayerLayer() }
        val avPlayerViewController = remember { AVPlayerViewController() }
        avPlayerViewController.player = player
        avPlayerViewController.showsPlaybackControls = true

        playerLayer.player = player
        UIKitView(
            modifier = modifier,
            factory = {
                // Create a UIView to hold the AVPlayerLayer
                val playerContainer = UIView()
                playerContainer.addSubview(avPlayerViewController.view)
                // Return the playerContainer as the root UIView
                playerContainer
            },
            onResize = { view: UIView, rect: CValue<CGRect> ->
                CATransaction.begin()
                CATransaction.setValue(true, kCATransactionDisableActions)
                view.layer.setFrame(rect)
                playerLayer.setFrame(rect)
                avPlayerViewController.view.layer.frame = rect
                CATransaction.commit()
            },
            update = { view ->
                player.play()
                avPlayerViewController.player!!.play()
            }
        )
    }

    override fun hasMedia(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isPlaying(): Boolean {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    override fun pause() {
        TODO("Not yet implemented")
    }

    override fun play() {
        TODO("Not yet implemented")
    }

    override fun isSeekable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun seekTo(toPositionMs: Long) {
        TODO("Not yet implemented")
    }

    override fun currentPositionMs(): Long {
        TODO("Not yet implemented")
    }

    override fun switchAspectRatio(): String {
        TODO("Not yet implemented")
    }
}