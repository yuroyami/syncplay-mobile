package app.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import app.preferences.Preferences
import app.preferences.value
import app.room.RoomViewmodel
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The room, as something Android's media stack understands.
 *
 * The service used to be a bare foreground notification: it kept the process alive and showed a
 * line of text, and that was all. No lock-screen controls, no headset button, nothing on the
 * notification but a title. That is what issue 125 is really about.
 *
 * [SimpleBasePlayer] exists for exactly this: it is a Player the platform can talk to, backed by
 * whatever actually plays. All five engines go through it, because none of them are Media3
 * players and one of them is VLCKit.
 *
 * Every control routes through the room's dispatcher rather than the engine, so a pause from the
 * lock screen is a pause the whole room hears, and one blocked by readiness is blocked here too.
 */
@UnstableApi
class RoomMediaSessionPlayer(
    private val viewmodel: RoomViewmodel,
    looper: android.os.Looper,
) : SimpleBasePlayer(looper) {

    /**
     * SimpleBasePlayer only re-reads [getState] when it is told to, so the room's own flows have
     * to poke it. Without this the lock screen shows whatever was true when the session was
     * built and never changes.
     */
    private val watcher = viewmodel.viewModelScope.launch(Dispatchers.Main.immediate) {
        combine(
            viewmodel.playerManager.isNowPlaying,
            viewmodel.playerManager.media,
            viewmodel.playerManager.timeFullMillis,
        ) { playing: Boolean, _, _ -> playing }.collect { invalidateState() }
    }

    /** Stops watching. Called when the session is released. */
    fun stopWatching() = watcher.cancel()

    override fun getState(): State {
        val manager = viewmodel.playerManager
        val media = manager.media.value
        val playing = manager.isNowPlaying.value

        val item = MediaItem.Builder()
            .setMediaId(media?.fileName ?: "none")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(media?.fileName ?: viewmodel.session.currentRoom)
                    .setArtist(viewmodel.session.currentRoom)
                    .build()
            )
            .build()

        return State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_SEEK_BACK,
                        Player.COMMAND_SEEK_FORWARD,
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_METADATA,
                        Player.COMMAND_GET_TIMELINE,
                    )
                    .build()
            )
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (media == null) Player.STATE_IDLE else Player.STATE_READY)
            .setPlaylist(listOf(MediaItemData.Builder(item.mediaId).setMediaItem(item)
                .setDurationUs(manager.timeFullMillis.value.coerceAtLeast(0) * 1000)
                .build()))
            .setContentPositionMs { manager.estimatedPositionMs() }
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        // Through the dispatcher, not the engine: the room has to hear it, and readiness has to
        // be able to refuse it.
        viewmodel.dispatcher.controlPlayback(
            if (playWhenReady) Playback.PLAY else Playback.PAUSE,
            tellServer = true,
        )
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        val from = viewmodel.playerManager.estimatedPositionMs()
        val target = when (seekCommand) {
            Player.COMMAND_SEEK_BACK -> from - backJumpMs()
            Player.COMMAND_SEEK_FORWARD -> from + forwardJumpMs()
            else -> positionMs
        }
        // The one seek path, so the room is told and the jump can be undone.
        viewmodel.dispatcher.seek(target.coerceAtLeast(0L), fromMs = from)
        return Futures.immediateVoidFuture()
    }

    /** The app's own jump keys, so a lock-screen skip matches an in-room one. */
    private fun backJumpMs(): Long = Preferences.SEEK_BACKWARD_JUMP.value().toLong() * 1000

    private fun forwardJumpMs(): Long = Preferences.SEEK_FORWARD_JUMP.value().toLong() * 1000
}
