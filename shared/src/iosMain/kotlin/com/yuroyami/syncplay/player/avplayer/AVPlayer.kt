package com.yuroyami.syncplay.player.avplayer

import androidx.compose.runtime.Composable
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
    lateinit var avView:  AVPlayerViewController
    var playerlayer: AVPlayerLayer? = null
    private var currentMedia: AVPlayerItem? = null

    val avMainScope = CoroutineScope(Dispatchers.Main)
    val avBGScope = CoroutineScope(Dispatchers.IO)

    override fun initialize() {
        avplayer = AVPlayer(uRL = NSURL.new()!!)
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