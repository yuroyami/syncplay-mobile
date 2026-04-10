package app.room

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.Screen
import app.home.JoinConfig
import app.player.PlayerImpl
import app.player.PlayerManager
import app.player.models.MediaFile
import app.preferences.Preferences
import app.preferences.value
import app.protocol.ProtocolManager
import app.protocol.Session
import app.protocol.event.RoomCallback
import app.protocol.event.RoomEventDispatcher
import app.protocol.models.TlsState
import app.protocol.network.NetworkManager
import app.room.sharedplaylist.SharedPlaylistManager
import app.utils.availablePlatformPlayerEngines
import app.utils.instantiateNetworkManager
import app.utils.loggy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_file_mismatch_warning_core
import syncplaymobile.shared.generated.resources.room_file_mismatch_warning_duration
import syncplaymobile.shared.generated.resources.room_file_mismatch_warning_name
import syncplaymobile.shared.generated.resources.room_file_mismatch_warning_size

/**
 * ViewModel for the Syncplay room screen where synchronized playback occurs.
 *
 * Coordinates all room-level managers including networking, media playback, protocol handling,
 * session state, and user interactions. Supports both online synchronized rooms and solo mode.
 *
 * @property joinConfig The room connection configuration, or null for solo mode
 * @property backStack The navigation stack for leaving the room
 */
class RoomViewmodel(val joinConfig: JoinConfig?, val backStack: SnapshotStateList<Screen>) : ViewModel() {

    /************ Managers ***************/

    /** Manages and holds UI state for the room screen */
    val uiState: RoomUiStateManager by lazy { RoomUiStateManager(this) }

    /** Manages media player lifecycle, controls, and state */
    val playerManager: PlayerManager by lazy { PlayerManager(this) }

    /** Manages the network connection and communication with the Syncplay server */
    lateinit var networkManager: NetworkManager

    /** Manages the Syncplay protocol and its events */
    val protocol: ProtocolManager by lazy { ProtocolManager(this) }

    /** Manages callbacks from protocol events (e.g., when someone pauses) - receiving actions */
    val callback: RoomCallback by lazy { RoomCallback(this) }

    /** Manages actions performed by the user to send to the server - sending actions */
    val dispatcher: RoomEventDispatcher by lazy { RoomEventDispatcher(this) }

    /** Manages the shared playlist and all playlist-related functionality */
    val playlistManager: SharedPlaylistManager by lazy { SharedPlaylistManager(this) }

    /**
     * List of seek operations as pairs of (fromPosition, toPosition) in milliseconds.
     * Used for tracking and potentially reverting seek operations.
     */
    val seeks = mutableListOf<Pair<Long, Long>>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            networkManager = instantiateNetworkManager()

            launch {
                val engine = availablePlatformPlayerEngines.first { it.name == Preferences.PLAYER_ENGINE.value() }
                playerManager.player = engine.createImpl(this@RoomViewmodel)
                playerManager.isPlayerReady.value = true
            }

            joinConfig?.let {
                launch {
                    session.serverHost = joinConfig.ip.takeIf { it != "syncplay.pl" } ?: "151.80.32.178"
                    session.serverPort = joinConfig.port
                    session.currentUsername = joinConfig.user
                    session.currentRoom = joinConfig.room
                    session.currentPassword = joinConfig.pw

                    /** Connecting (via TLS or noTLS) */
                    val tls = Preferences.TLS_ENABLE.value()
                    if (tls && networkManager.supportsTLS()) {
                        callback.onTLSCheck()
                        networkManager.tls = TlsState.TLS_ASK
                    }

                    networkManager.connect()
                }
            }
        }
    }

    /**
     * Exits the current room and returns to the home screen.
     */
    fun goHome() {
        backStack.removeAt(backStack.lastIndex)
    }

    /**
     * Checks for file mismatches between the local media and other users' files.
     */
    fun checkFileMismatches() {
        if (isSoloMode) return
        if (!Preferences.FILE_MISMATCH_WARNING.value()) return //Return if user doesn't want warnings

        viewModelScope.launch {
            val localMedia = media ?: return@launch //No media is loaded

            for (user in session.userList.value) {
                if (user.name == session.currentUsername) continue //We ain't gonna compare with ourselves
                val theirFile = user.file ?: continue //User has no file

                // Map mismatch conditions to their respective warning messages
                val mismatches = listOf(
                    (localMedia.fileName != theirFile.fileName) to Res.string.room_file_mismatch_warning_name,
                    (localMedia.fileDuration != theirFile.fileDuration) to Res.string.room_file_mismatch_warning_duration,
                    (localMedia.fileSize != theirFile.fileSize) to Res.string.room_file_mismatch_warning_size
                )

                // If all three mismatch, skip showing a warning
                val matchingMismatches = mismatches.filter { it.first }
                if (matchingMismatches.isEmpty() || matchingMismatches.size == 3) continue

                // Build warning message dynamically
                val warning = buildString {
                    append(getString(Res.string.room_file_mismatch_warning_core, user.name))
                    mismatches.filter { it.first }
                        .forEach { append(getString(it.second)) }
                }

                dispatcher.broadcastMessage(message = { warning }, isChat = false, isError = true)
            }
        }
    }

    /**
     * Indicates whether the room is in solo mode (offline playback).
     * When true, online-only components like networking and session sync are disabled.
     */
    val isSoloMode: Boolean
        get() = joinConfig == null


    val osdMsg = mutableStateOf("")
    var osdJob: Job? = null
    fun dispatchOSD(getter: suspend () -> String) {
        val durationSec = app.preferences.Preferences.OSD_DURATION.value()
        if (durationSec <= 0) return

        runCatching {
            osdJob?.cancel(null)
        }
        osdJob = viewModelScope.launch(Dispatchers.IO) {
            osdMsg.value = getter()
            delay(durationSec * 1000L)
            osdMsg.value = ""
        }
    }

    /************ Extension Properties for Quick Access ***************/

    /** Quick access to the current media player instance */
    val player: PlayerImpl
        get() = playerManager.player

    /** Quick access to the current room session state */
    val session: Session
        get() = protocol.session

    /** Quick access to the currently loaded media file, if any */
    var media: MediaFile?
        get() = playerManager.media.value
        set(value) {
            playerManager.media.value = value
        }

    /** Quick access to whether the current media contains video */
    val hasVideo: StateFlow<Boolean>
        get() = playerManager.hasVideo

    /**
     * Cleans up all managers and resources when the ViewModel is destroyed.
     * Ensures proper shutdown of network connections, player, and other subsystems.
     */
    override fun onCleared() {
        loggy("²²²²²²²²²²²² Clearing viewmodel")
        playerManager.invalidate()
        networkManager.invalidate()
        uiState.invalidate()
        protocol.invalidate()
        super.onCleared()
    }
}