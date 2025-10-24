package com.yuroyami.syncplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.managers.LifecycleManager
import com.yuroyami.syncplay.managers.NetworkManager
import com.yuroyami.syncplay.managers.OSDManager
import com.yuroyami.syncplay.managers.OnRoomEventManager
import com.yuroyami.syncplay.managers.PlayerManager
import com.yuroyami.syncplay.managers.ProtocolManager
import com.yuroyami.syncplay.managers.RoomActionManager
import com.yuroyami.syncplay.managers.SessionManager
import com.yuroyami.syncplay.managers.SharedPlaylistManager
import com.yuroyami.syncplay.managers.SnackManager
import com.yuroyami.syncplay.managers.ThemeManager
import com.yuroyami.syncplay.managers.UIManager
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.MISC_PLAYER_ENGINE
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_TLS_ENABLE
import com.yuroyami.syncplay.managers.datastore.valueSuspendingly
import com.yuroyami.syncplay.managers.player.BasePlayer
import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.models.JoinConfig
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.ui.screens.adam.Screen
import com.yuroyami.syncplay.utils.ProtocolDsl
import com.yuroyami.syncplay.utils.availablePlatformPlayerEngines
import com.yuroyami.syncplay.utils.instantiateNetworkManager
import com.yuroyami.syncplay.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SyncplayViewmodel: ViewModel() {

    /************ Managers ***************/
    /** Manages and holds UI state */
    val uiManager: UIManager by lazy { UIManager(this) }

    /** Manages anything related to the player */
    val playerManager: PlayerManager by lazy { PlayerManager(this) }

    /** Manages the network engine and events */
    lateinit var networkManager: NetworkManager

    /** Manages the protocol and its  events */
    val protocolManager: ProtocolManager by lazy { ProtocolManager(this) }

    /** Manages the current session instance (which is the room session */
    val sessionManager: SessionManager by lazy { SessionManager(this) }

    /** Manages callback from the protocol (e.g: When someone pauses), in other words: Receiving actions */
    val callbackManager: OnRoomEventManager by lazy { OnRoomEventManager(this) }

    /** Manages actions performed by the user to send them to the server (Sending actions) */
    val actionManager: RoomActionManager by lazy { RoomActionManager(this) }

    /** Manages events of the lifecycle of the Android Activity or the iOS ViewController (tracking when it goes to background) */
    val lifecycleManager: LifecycleManager by lazy { LifecycleManager(this) }

    /** Manages the Shared playlist and anything related to it */
    val playlistManager: SharedPlaylistManager by lazy { SharedPlaylistManager(this) }

    /** Displays status messages in the room */
    val osdManager: OSDManager by lazy { OSDManager(this) }

    /** Displays snack messages in home screen */
    val snackManager: SnackManager by lazy { SnackManager(this) }

    /** Manages themes */
    val themeManager: ThemeManager by lazy { ThemeManager(this) }

    /*** Other */

    /** Not to be confused with the protocol's ping. This is the result of the periodic
     * ICMP pinging which will be shown on the top-center of the room.
     * There are devices that don't support this natively, like Android emulators.
     */
    val ping = MutableStateFlow<Int?>(null)

    var setReadyDirectly = false

    val seeks = mutableListOf<Pair<Long, Long>>()

    @ProtocolDsl
    fun joinRoom(joinConfig: JoinConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            joinConfig.save() //Remembering info

            setReadyDirectly = valueSuspendingly(DataStoreKeys.PREF_READY_FIRST_HAND, true)

            networkManager = instantiateNetworkManager(engine = NetworkManager.getPreferredEngine())

            sessionManager.session.serverHost = joinConfig.ip.takeIf { it != "syncplay.pl" } ?: "151.80.32.178"
            sessionManager.session.serverPort = joinConfig.port
            sessionManager.session.currentUsername = joinConfig.user
            sessionManager.session.currentRoom = joinConfig.room
            sessionManager.session.currentPassword = joinConfig.pw

            launch(Dispatchers.Main) {
                platformCallback.onRoomEnterOrLeave(PlatformCallback.RoomEvent.ENTER)
                uiManager.navigateTo(Screen.Room)
            }

            val defaultEngine = availablePlatformPlayerEngines.first { it.isDefault }.name //TODO
            val engine = availablePlatformPlayerEngines.first { it.name == valueSuspendingly(MISC_PLAYER_ENGINE, defaultEngine) }
            playerManager.player = engine.instantiate(this@SyncplayViewmodel)

            /** Connecting (via TLS or noTLS) */
            val tls = valueSuspendingly(PREF_TLS_ENABLE, default = true)
            if (tls && networkManager.supportsTLS()) {
                callbackManager.onTLSCheck()
                networkManager.tls = Constants.TLS.TLS_ASK
            }

            networkManager.connect()
        }
    }

    fun leaveRoom() {
        networkManager.terminateExistingConnection()
        viewModelScope.launch(Dispatchers.Main.immediate) {
            player?.destroy()
        }

        platformCallback.onRoomEnterOrLeave(PlatformCallback.RoomEvent.LEAVE)

        uiManager.invalidate() //<-- This will navigate back to home screen automatically
        playerManager.invalidate()
        networkManager.invalidate()
        protocolManager.invalidate()
        sessionManager.invalidate()
        osdManager.invalidate()
    }

    /** Mismatches are: Name, Size, Duration. If 3 mismatches are detected, no error is thrown
     * since that would mean that the two files are completely and obviously different.*/
    //TODO/ This needs full refactoring
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

    /** Tells us whether we're in solo mode to deactivate some online components */
    val isSoloMode: Boolean
        get() = uiManager.backStack.lastOrNull() == Screen.SoloMode

    /** Extension property to quickly access some managers' properties */
    val player: BasePlayer?
        get() = playerManager.player

    val session: SessionManager.Session
        get() = sessionManager.session

    val media: MediaFile?
        get() = playerManager.media.value

    val hasVideo: StateFlow<Boolean>
        get() = playerManager.hasVideo

    /** End of viewmodel */
    override fun onCleared() {
        super.onCleared()
    }
}