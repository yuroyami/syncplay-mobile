package app.player.kite

import io.github.yuroyami.kiteplayer.TrackId

/**
 * What a pick on the video tab asks of KitePlayer.
 *
 * KitePlayer, 0.2.0 as well as 0.0.27, answers every video track change by closing the stream,
 * opening it again and seeking back. On a network link that stopped the sound for about a second.
 * So "Video off" and a pick of the stream that already plays only hide or show the picture, and
 * only another stream changes the engine's track.
 */
internal enum class VideoPick { Hide, Show, Switch }

internal fun videoPickOf(requested: TrackId?, playing: TrackId?): VideoPick = when (requested) {
    null -> VideoPick.Hide
    playing -> VideoPick.Show
    else -> VideoPick.Switch
}

/**
 * Whether to park video decoding (KitePlayer's `setVideoEnabled(false)`): the file has a picture,
 * it is hidden, and no visualizer draws in its place. Under a drawing visualizer the video keeps
 * decoding, so the picture comes back at once. Waking a parked video costs one precise seek.
 */
internal fun shouldParkVideo(hasVideo: Boolean, pictureShown: Boolean, visualizerDrawing: Boolean): Boolean =
    hasVideo && !pictureShown && !visualizerDrawing
