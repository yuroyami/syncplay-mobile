package com.yuroyami.syncplay.viewmodels

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.PlatformCallback
import com.yuroyami.syncplay.managers.LifecycleManager
import com.yuroyami.syncplay.managers.OSDManager
import com.yuroyami.syncplay.managers.OnRoomEventManager
import com.yuroyami.syncplay.managers.RoomActionManager
import com.yuroyami.syncplay.managers.SessionManager
import com.yuroyami.syncplay.managers.SharedPlaylistManager
import com.yuroyami.syncplay.managers.UIManager
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.datastore.valueSuspendingly
import com.yuroyami.syncplay.managers.network.NetworkManager
import com.yuroyami.syncplay.managers.player.BasePlayer
import com.yuroyami.syncplay.managers.player.PlayerManager
import com.yuroyami.syncplay.managers.protocol.ProtocolManager
import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.models.JoinConfig
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.ui.screens.Screen
import com.yuroyami.syncplay.utils.availablePlatformPlayerEngines
import com.yuroyami.syncplay.utils.instantiateNetworkManager
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    val playerManager: PlayerManager by lazy { PlayerManager(this) }

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
     * Whether to immediately set the user as ready when joining the room.
     * Loaded from user preferences.
     */
    var setReadyDirectly = false

    /**
     * List of seek operations as pairs of (fromPosition, toPosition) in milliseconds.
     * Used for tracking and potentially reverting seek operations.
     */
    val seeks = mutableListOf<Pair<Long, Long>>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            setReadyDirectly = valueSuspendingly(DataStoreKeys.PREF_READY_FIRST_HAND, true)

            networkManager = instantiateNetworkManager(engine = NetworkManager.getPreferredEngine())

            joinConfig?.let {
                launch {
                    val defaultEngine = availablePlatformPlayerEngines.first { it.isDefault }.name //TODO
                    val engine = availablePlatformPlayerEngines.first { it.name == valueSuspendingly(DataStoreKeys.MISC_PLAYER_ENGINE, defaultEngine) }
                    playerManager.player = engine.instantiate(this@RoomViewmodel)
                    playerManager.isPlayerReady.value = true
                }
                launch {
                    sessionManager.session.serverHost = joinConfig.ip.takeIf { it != "syncplay.pl" } ?: "151.80.32.178"
                    sessionManager.session.serverPort = joinConfig.port
                    sessionManager.session.currentUsername = joinConfig.user
                    sessionManager.session.currentRoom = joinConfig.room
                    sessionManager.session.currentPassword = joinConfig.pw

                    /** Connecting (via TLS or noTLS) */
                    val tls = valueSuspendingly(DataStoreKeys.PREF_TLS_ENABLE, default = true)
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
     * Notifies the platform of the room exit event.
     */
    fun leaveRoom() {
        platformCallback.onRoomEnterOrLeave(PlatformCallback.RoomEvent.LEAVE)

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
     *
     * TODO: This needs full refactoring
     */
    fun checkFileMismatches() {
        if (isSoloMode) return

//        viewModelScope.launch {
//            /** First, we check if user wanna be notified about file mismatchings */
//            val pref = valueBlockingly(PREF_FILE_MISMATCH_WARNING, true)
//
//            if (!pref) return@launch
//
//            for (user in p.session.userList.value) {
//                val theirFile = user.file ?: continue /* If they have no file, iterate unto next */
//
//                val nameMismatch = (media?.fileName != theirFile.fileName) and (media?.fileNameHashed != theirFile.fileNameHashed)
//                val durationMismatch = media?.fileDuration != theirFile.fileDuration
//                val sizeMismatch = (media?.fileSize != theirFile.fileSize) and (media?.fileSizeHashed != theirFile.fileSizeHashed)
//
//                if (nameMismatch && durationMismatch && sizeMismatch) continue /* 2 mismatches or less */
//
//
//                var warning = getString(Res.string.room_file_mismatch_warning_core, user.name)
//
//                if (nameMismatch) warning += getString(Res.string.room_file_mismatch_warning_name)
//                if (durationMismatch) warning += getString(Res.string.room_file_mismatch_warning_duration)
//                if (sizeMismatch) warning += getString(Res.string.room_file_mismatch_warning_size)
//
//                broadcastMessage(message = { warning }, isChat = false, isError = true)
//            }
//        }
    }

    /**
     * Indicates whether the room is in solo mode (offline playback).
     * When true, online-only components like networking and session sync are disabled.
     */
    val isSoloMode: Boolean
        get() = joinConfig == null

    /************ Extension Properties for Quick Access ***************/

    /** Quick access to the current media player instance */
    val player: BasePlayer
        get() = playerManager.player

    /** Quick access to the current room session state */
    val session: SessionManager.Session
        get() = sessionManager.session

    /** Quick access to the currently loaded media file, if any */
    val media: MediaFile?
        get() = playerManager.media.value

    /** Quick access to whether the current media contains video */
    val hasVideo: StateFlow<Boolean>
        get() = playerManager.hasVideo

    /**
     * Cleans up all managers and resources when the ViewModel is destroyed.
     * Ensures proper shutdown of network connections, player, and other subsystems.
     */
    override fun onCleared() {
        loggy("²²²²²²²²²²²² Clearing viewmodel")
        networkManager.invalidate()
        uiManager.invalidate()
        playerManager.invalidate()
        protocolManager.invalidate()
        sessionManager.invalidate()
        osdManager.invalidate()
        super.onCleared()
    }
}