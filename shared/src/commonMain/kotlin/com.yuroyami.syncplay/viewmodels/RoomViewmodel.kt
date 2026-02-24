package com.yuroyami.syncplay.viewmodels

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.managers.LifecycleManager
import com.yuroyami.syncplay.managers.OSDManager
import com.yuroyami.syncplay.managers.OnRoomEventManager
import com.yuroyami.syncplay.managers.RoomActionManager
import com.yuroyami.syncplay.managers.SessionManager
import com.yuroyami.syncplay.managers.SharedPlaylistManager
import com.yuroyami.syncplay.managers.UIManager
import com.yuroyami.syncplay.managers.network.NetworkManager
import com.yuroyami.syncplay.managers.player.PlayerImpl
import com.yuroyami.syncplay.managers.player.VideoEngineManager
import com.yuroyami.syncplay.managers.preferences.Preferences.FILE_MISMATCH_WARNING
import com.yuroyami.syncplay.managers.preferences.Preferences.PLAYER_ENGINE
import com.yuroyami.syncplay.managers.preferences.Preferences.TLS_ENABLE
import com.yuroyami.syncplay.managers.preferences.value
import com.yuroyami.syncplay.managers.protocol.ProtocolManager
import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.models.JoinConfig
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.ui.screens.Screen
import com.yuroyami.syncplay.utils.availablePlatformVideoEngines
import com.yuroyami.syncplay.utils.instantiateNetworkManager
import com.yuroyami.syncplay.utils.loggy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
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
    val uiManager: UIManager by lazy { UIManager(this) }

    /** Manages media player lifecycle, controls, and state */
    val videoEngineManager: VideoEngineManager by lazy { VideoEngineManager(this) }

    /** Manages the network connection and communication with the Syncplay server */
    lateinit var networkManager: NetworkManager

    /** Manages the Syncplay protocol and its events */
    val protocolManager: ProtocolManager by lazy { ProtocolManager(this) }

    /** Manages the current room session state and user list */
    val sessionManager: SessionManager by lazy { SessionManager(this) }

    /** Manages callbacks from protocol events (e.g., when someone pauses) - receiving actions */
    val callbackManager: OnRoomEventManager by lazy { OnRoomEventManager(this) }

    /** Manages actions performed by the user to send to the server - sending actions */
    val actionManager: RoomActionManager by lazy { RoomActionManager(this) }

    /** Manages lifecycle events of the Activity/ViewController (tracking background state) */
    val lifecycleManager: LifecycleManager by lazy { LifecycleManager(this) }

    /** Manages the shared playlist and all playlist-related functionality */
    val playlistManager: SharedPlaylistManager by lazy { SharedPlaylistManager(this) }

    /** Displays temporary status messages (OSD) in the room */
    val osdManager: OSDManager by lazy { OSDManager(this) }

    /**
     * Network ping latency in milliseconds with the server.
     *
     * Not to be confused with the protocol's ping. This is the result of periodic
     * ICMP pinging displayed at the top-center of the room screen.
     * Note: Some devices don't support this natively (e.g., Android emulators).
     */
    val ping = MutableStateFlow<Int?>(null)

    /**
     * List of seek operations as pairs of (fromPosition, toPosition) in milliseconds.
     * Used for tracking and potentially reverting seek operations.
     */
    val seeks = mutableListOf<Pair<Long, Long>>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            networkManager = instantiateNetworkManager()

            launch {
                val engine = availablePlatformVideoEngines.first { it.name == PLAYER_ENGINE.value() }
                videoEngineManager.player = engine.createImpl(this@RoomViewmodel)
                videoEngineManager.isPlayerReady.value = true
            }

            joinConfig?.let {
                launch {
                    sessionManager.session.serverHost = joinConfig.ip.takeIf { it != "syncplay.pl" } ?: "151.80.32.178"
                    sessionManager.session.serverPort = joinConfig.port
                    sessionManager.session.currentUsername = joinConfig.user
                    sessionManager.session.currentRoom = joinConfig.room
                    sessionManager.session.currentPassword = joinConfig.pw

                    /** Connecting (via TLS or noTLS) */
                    val tls = TLS_ENABLE.value()
                    if (tls && networkManager.supportsTLS()) {
                        callbackManager.onTLSCheck()
                        networkManager.tls = Constants.TLS.TLS_ASK
                    }

                    networkManager.connect()
                }
            }
        }
    }

    /**
     * Exits the current room and returns to the home screen.
     */
    fun leaveRoom() {
        backStack.removeLast()
    }

    /**
     * Checks for file mismatches between the local media and other users' files.
     *
     * Compares file name, size, and duration. If all three differ, the files are considered
     * completely different and no warning is shown. Otherwise, broadcasts a mismatch warning.
     *
     * Mismatches checked: Name, Size, Duration. If 3 mismatches are detected, no error is thrown
     * since that would mean the two files are completely and obviously different.
     */
    fun checkFileMismatches() {
        if (isSoloMode) return

        viewModelScope.launch {
            if (!FILE_MISMATCH_WARNING.value()) return@launch //Return if user doesn't want warnings
            val localMedia = media ?: return@launch //No media is loaded

            for (user in session.userList.value) {
                val theirFile = user.file ?: continue //User has no file

                // Map mismatch conditions to their respective warning messages
                val mismatches = listOf(
                    (localMedia.fileName != theirFile.fileName) to Res.string.room_file_mismatch_warning_name,
                    (localMedia.fileDuration != theirFile.fileDuration) to Res.string.room_file_mismatch_warning_duration,
                    (localMedia.fileSize != theirFile.fileSize) to Res.string.room_file_mismatch_warning_size
                )

                // If all three mismatch, skip showing a warning
                if (mismatches.all { it.first }) continue

                // Build warning message dynamically
                val warning = buildString {
                    append(getString(Res.string.room_file_mismatch_warning_core, user.name))
                    mismatches.filter { it.first }
                        .forEach { append(getString(it.second)) }
                }

                actionManager.broadcastMessage(message = { warning }, isChat = false, isError = true)
            }
        }
    }

    /**
     * Indicates whether the room is in solo mode (offline playback).
     * When true, online-only components like networking and session sync are disabled.
     */
    val isSoloMode: Boolean
        get() = joinConfig == null

    /************ Extension Properties for Quick Access ***************/

    /** Quick access to the current media player instance */
    val player: PlayerImpl
        get() = videoEngineManager.player

    /** Quick access to the current room session state */
    val session: SessionManager.Session
        get() = sessionManager.session

    /** Quick access to the currently loaded media file, if any */
    val media: MediaFile?
        get() = videoEngineManager.media.value

    /** Quick access to whether the current media contains video */
    val hasVideo: StateFlow<Boolean>
        get() = videoEngineManager.hasVideo

    /**
     * Cleans up all managers and resources when the ViewModel is destroyed.
     * Ensures proper shutdown of network connections, player, and other subsystems.
     */
    override fun onCleared() {
        loggy("²²²²²²²²²²²² Clearing viewmodel")
        videoEngineManager.invalidate()
        networkManager.invalidate()
        uiManager.invalidate()
        protocolManager.invalidate()
        sessionManager.invalidate()
        osdManager.invalidate()
        super.onCleared()
    }
}