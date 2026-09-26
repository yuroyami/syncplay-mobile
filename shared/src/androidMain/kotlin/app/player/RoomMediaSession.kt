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
import app.room.RoomViewmodel

/**
 * The room as a Media3 player, so that Android's media stack can show what the room plays. A room
 * is the group of people watching together. [SimpleBasePlayer] is a Player that the platform can
 * talk to, backed by any playback code, so every Android engine goes through it.
 *
 * It offers no controls, on purpose. A headset button, the lock screen and the media notification
 * must never pause, play or seek the room: a tap on an earbud that almost fell out would stop
 * everyone. The session still exists, so a headset button reaches this app and does nothing,
 * instead of starting music in another app.
 */
@UnstableApi
class RoomMediaSessionPlayer(
    private val viewmodel: RoomViewmodel,
    looper: android.os.Looper,
) : SimpleBasePlayer(looper) {

    /**
     * SimpleBasePlayer reads [getState] again only when told to, so the room's flows call
     * invalidateState. Without this, the lock screen shows the state from when the session was
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
                // Read commands only: the system can show the room, and cannot change it.
                Player.Commands.Builder()
                    .addAll(
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
}
