package com.yuroyami.syncplay.managers.player.mpv

import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.runBlocking

@Suppress("OVERRIDE_DEPRECATION")
@UnstableApi
fun MpvPlayer.mapToMedia3BasePlayer(): Player {
    val mpvPlayer = this

    return object : Player {
        private val listeners = mutableListOf<Player.Listener>()

        override fun getApplicationLooper(): Looper = Looper.getMainLooper()

        override fun addListener(listener: Player.Listener) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }

        override fun removeListener(listener: Player.Listener) {
            listeners.remove(listener)
        }

        override fun setMediaItems(mediaItems: List<MediaItem>) {
            // MPV doesn't support playlists in the same way, load first item
            if (mediaItems.isNotEmpty()) {
                setMediaItem(mediaItems[0])
            }
        }

        override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
            setMediaItems(mediaItems)
        }

        override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
            if (startIndex in mediaItems.indices) {
                setMediaItem(mediaItems[startIndex], startPositionMs)
            }
        }

        override fun setMediaItem(mediaItem: MediaItem) {
            setMediaItem(mediaItem, 0)
        }

        override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {

                val uri = mediaItem.localConfiguration?.uri?.toString() ?: return
                mpvPlayer.mpvView.playFile(uri)
                if (startPositionMs > 0) {
                    seekTo(startPositionMs)
                }
        }

        override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
            setMediaItem(mediaItem, if (resetPosition) 0 else C.TIME_UNSET)
        }

        override fun addMediaItem(mediaItem: MediaItem) {
            // MPV doesn't support adding to playlist in our implementation
        }

        override fun addMediaItem(index: Int, mediaItem: MediaItem) {
            // MPV doesn't support adding to playlist in our implementation
        }

        override fun addMediaItems(mediaItems: List<MediaItem>) {
            // MPV doesn't support adding to playlist in our implementation
        }

        override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
            // MPV doesn't support adding to playlist in our implementation
        }

        override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
            // Not supported
        }

        override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
            // Not supported
        }

        override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
            if (index == 0) {
                setMediaItem(mediaItem)
            }
        }

        override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) {
            if (fromIndex == 0 && mediaItems.isNotEmpty()) {
                setMediaItem(mediaItems[0])
            }
        }

        override fun removeMediaItem(index: Int) {
            if (index == 0) {
                runBlocking { mpvPlayer.mpvView.destroy() }
            }
        }

        override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
            if (fromIndex == 0) {
                clearMediaItems()
            }
        }

        override fun clearMediaItems() {
                if (mpvPlayer.isInitialized) {
                    MPVLib.command(arrayOf("stop"))
                }

        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SET_SPEED_AND_PITCH,
                Player.COMMAND_SET_VOLUME,
                Player.COMMAND_GET_VOLUME,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_MEDIA_ITEMS_METADATA,
                Player.COMMAND_SET_MEDIA_ITEMS_METADATA,
                Player.COMMAND_CHANGE_MEDIA_ITEMS -> mpvPlayer.isInitialized
                else -> false
            }
        }

        override fun canAdvertiseSession(): Boolean = true

        override fun getAvailableCommands(): Player.Commands {
            return Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_PREPARE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SET_SPEED_AND_PITCH,
                    Player.COMMAND_SET_VOLUME,
                    Player.COMMAND_GET_VOLUME
                )
                .build()
        }

        override fun prepare() {
            // MPV auto-prepares when file is loaded
        }

        override fun getPlaybackState(): Int {
            return when {
                !mpvPlayer.isInitialized -> Player.STATE_IDLE
                runBlocking { mpvPlayer.hasMedia() } -> {
                    if (mpvPlayer.mpvView.paused) Player.STATE_READY else Player.STATE_READY
                }
                else -> Player.STATE_IDLE
            }
        }

        override fun getPlaybackSuppressionReason(): Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE

        override fun isPlaying(): Boolean {
            return runBlocking { mpvPlayer.isPlaying() }
        }

        override fun getPlayerError(): PlaybackException? = null

        override fun play() {
            runBlocking { mpvPlayer.play() }
        }

        override fun pause() {
            runBlocking { mpvPlayer.pause() }
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) play() else pause()
        }

        override fun getPlayWhenReady(): Boolean = isPlaying

        override fun setRepeatMode(repeatMode: Int) {
            when (repeatMode) {
                Player.REPEAT_MODE_OFF -> MPVLib.setPropertyString("loop-file", "no")
                Player.REPEAT_MODE_ONE -> MPVLib.setPropertyString("loop-file", "inf")
                Player.REPEAT_MODE_ALL -> MPVLib.setPropertyString("loop-playlist", "inf")
            }
        }

        override fun getRepeatMode(): Int {
            val loopFile = MPVLib.getPropertyString("loop-file")
            return when (loopFile) {
                "inf" -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }

        override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
            // Not supported in MPV
        }

        override fun getShuffleModeEnabled(): Boolean = false

        override fun isLoading(): Boolean {
            return playbackState == Player.STATE_BUFFERING
        }

        override fun seekToDefaultPosition() {
            seekTo(0)
        }

        override fun seekToDefaultPosition(mediaItemIndex: Int) {
            if (mediaItemIndex == 0) seekToDefaultPosition()
        }

        override fun seekTo(positionMs: Long) {
            mpvPlayer.seekTo(positionMs)
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            if (mediaItemIndex == 0) seekTo(positionMs)
        }

        override fun getSeekBackIncrement(): Long = 10000L

        override fun seekBack() {
            val newPos = (currentPosition - seekBackIncrement).coerceAtLeast(0)
            seekTo(newPos)
        }

        override fun getSeekForwardIncrement(): Long = 10000L

        override fun seekForward() {
            val newPos = (currentPosition + seekForwardIncrement).coerceAtMost(duration)
            seekTo(newPos)
        }

        override fun hasPreviousMediaItem(): Boolean = false

        override fun seekToPreviousMediaItem() {
            // Not supported
        }

        override fun getMaxSeekToPreviousPosition(): Long = 3000L

        override fun seekToPrevious() {
            seekToDefaultPosition()
        }

        override fun hasNextMediaItem(): Boolean = false

        override fun seekToNextMediaItem() {
            // Not supported
        }

        override fun seekToNext() {
            // Not supported
        }

        override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
            setPlaybackSpeed(playbackParameters.speed)
        }

        override fun setPlaybackSpeed(speed: Float) {
            MPVLib.setPropertyDouble("speed", speed.toDouble())
        }

        override fun getPlaybackParameters(): PlaybackParameters {
            val speed = MPVLib.getPropertyDouble("speed")?.toFloat() ?: 1f
            return PlaybackParameters(speed)
        }

        override fun stop() {
            runBlocking {
                mpvPlayer.pause()
                MPVLib.command(arrayOf("stop"))
            }
        }

        override fun release() {
            runBlocking { mpvPlayer.destroy() }
        }

        override fun getCurrentTracks(): Tracks = Tracks.EMPTY

        override fun getTrackSelectionParameters(): TrackSelectionParameters {
            return TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
        }

        override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
            // Handled internally by MPV
        }

        override fun getMediaMetadata(): MediaMetadata {
            return mpvPlayer.playerManager.media.value?.let {
                MediaMetadata.Builder()
                    .setTitle(it.fileName)
                    .build()
            } ?: MediaMetadata.EMPTY
        }

        override fun getPlaylistMetadata(): MediaMetadata = MediaMetadata.EMPTY

        override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
            // Not applicable
        }

        override fun getCurrentManifest(): Any? = null

        override fun getCurrentTimeline(): Timeline = Timeline.EMPTY

        override fun getCurrentPeriodIndex(): Int = 0

        override fun getCurrentWindowIndex(): Int = 0

        override fun getCurrentMediaItemIndex(): Int = 0

        override fun getNextWindowIndex(): Int = C.INDEX_UNSET

        override fun getNextMediaItemIndex(): Int = C.INDEX_UNSET

        override fun getPreviousWindowIndex(): Int = C.INDEX_UNSET

        override fun getPreviousMediaItemIndex(): Int = C.INDEX_UNSET

        override fun getCurrentMediaItem(): MediaItem? {
            return mpvPlayer.playerManager.media.value?.let { media ->
                MediaItem.Builder()
                    .setUri(media.location?.commonUri ?: "")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(media.fileName)
                            .build()
                    )
                    .build()
            }
        }

        override fun getMediaItemCount(): Int {
            return if (runBlocking { mpvPlayer.hasMedia() }) 1 else 0
        }

        override fun getMediaItemAt(index: Int): MediaItem {
            if (index == 0) {
                return currentMediaItem ?: MediaItem.EMPTY
            }
            throw IndexOutOfBoundsException()
        }

        override fun getDuration(): Long {
            return mpvPlayer.playerManager.timeFullMillis.value
        }

        override fun getCurrentPosition(): Long {
            return mpvPlayer.currentPositionMs()
        }

        override fun getBufferedPosition(): Long {
            return currentPosition
        }

        override fun getBufferedPercentage(): Int {
            val duration = getDuration()
            return if (duration > 0) {
                ((bufferedPosition * 100) / duration).toInt()
            } else 0
        }

        override fun getTotalBufferedDuration(): Long {
            return bufferedPosition - currentPosition
        }

        override fun isCurrentWindowDynamic(): Boolean = false

        override fun isCurrentMediaItemDynamic(): Boolean = false

        override fun isCurrentWindowLive(): Boolean = false

        override fun isCurrentMediaItemLive(): Boolean = false

        override fun getCurrentLiveOffset(): Long = C.TIME_UNSET

        override fun isCurrentWindowSeekable(): Boolean = runBlocking { mpvPlayer.isSeekable() }

        override fun isCurrentMediaItemSeekable(): Boolean = isCurrentWindowSeekable()

        override fun isPlayingAd(): Boolean = false

        override fun getCurrentAdGroupIndex(): Int = C.INDEX_UNSET

        override fun getCurrentAdIndexInAdGroup(): Int = C.INDEX_UNSET

        override fun getContentDuration(): Long = duration

        override fun getContentPosition(): Long = currentPosition

        override fun getContentBufferedPosition(): Long = bufferedPosition

        override fun getAudioAttributes(): AudioAttributes {
            return AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()
        }

        override fun setVolume(volume: Float) {
            val maxVolume = mpvPlayer.getMaxVolume()
            val newVolume = (volume * maxVolume).toInt()
            mpvPlayer.changeCurrentVolume(newVolume)
        }

        override fun getVolume(): Float {
            val current = mpvPlayer.getCurrentVolume().toFloat()
            val max = mpvPlayer.getMaxVolume().toFloat()
            return if (max > 0) current / max else 0f
        }

        override fun mute() {
            TODO("Not yet implemented")
        }

        override fun unmute() {
            TODO("Not yet implemented")
        }

        override fun clearVideoSurface() {
            // Handled by MPV view
        }

        override fun clearVideoSurface(surface: Surface?) {
            // Handled by MPV view
        }

        override fun setVideoSurface(surface: Surface?) {
            // Handled by MPV view
        }

        override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
            // Handled by MPV view
        }

        override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
            // Handled by MPV view
        }

        override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
            // Handled by MPV view
        }

        override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
            // Handled by MPV view
        }

        override fun setVideoTextureView(textureView: TextureView?) {
            // Handled by MPV view
        }

        override fun clearVideoTextureView(textureView: TextureView?) {
            // Handled by MPV view
        }

        override fun getVideoSize(): VideoSize {
            val width = MPVLib.getPropertyInt("width") ?: 0
            val height = MPVLib.getPropertyInt("height") ?: 0
            return VideoSize(width, height)
        }

        override fun getSurfaceSize(): Size {
            val videoSize = getVideoSize()
            return Size(videoSize.width, videoSize.height)
        }

        override fun getCurrentCues(): CueGroup = CueGroup.EMPTY_TIME_ZERO

        override fun getDeviceInfo(): DeviceInfo {
            return DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL)
                .setMaxVolume(mpvPlayer.getMaxVolume())
                .setMinVolume(0)
                .build()
        }

        override fun getDeviceVolume(): Int {
            return mpvPlayer.getCurrentVolume()
        }

        override fun isDeviceMuted(): Boolean {
            return mpvPlayer.getCurrentVolume() == 0
        }

        override fun setDeviceVolume(volume: Int) {
            mpvPlayer.changeCurrentVolume(volume)
        }

        override fun setDeviceVolume(volume: Int, flags: Int) {
            deviceVolume = volume
        }

        override fun increaseDeviceVolume() {
            val current = deviceVolume
            val max = deviceInfo.maxVolume
            deviceVolume = (current + 1).coerceAtMost(max)
        }

        override fun increaseDeviceVolume(flags: Int) {
            increaseDeviceVolume()
        }

        override fun decreaseDeviceVolume() {
            val current = deviceVolume
            deviceVolume = (current - 1).coerceAtLeast(0)
        }

        override fun decreaseDeviceVolume(flags: Int) {
            decreaseDeviceVolume()
        }

        override fun setDeviceMuted(muted: Boolean) {
            if (muted) {
                deviceVolume = 0
            } else {
                val max = deviceInfo.maxVolume
                deviceVolume = max / 2
            }
        }

        override fun setDeviceMuted(muted: Boolean, flags: Int) {
            isDeviceMuted = muted
        }

        override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
            // Audio attributes are handled by the audio manager in MpvPlayer
        }
    }
}