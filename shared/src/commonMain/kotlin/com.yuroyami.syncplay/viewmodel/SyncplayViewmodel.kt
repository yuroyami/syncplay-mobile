@file:Suppress("DeferredResultUnused")
package com.yuroyami.syncplay.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.models.JoinConfig
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.Message
import com.yuroyami.syncplay.models.TrackChoices
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.protocol.ProtocolCallback
import com.yuroyami.syncplay.protocol.SyncplayProtocol
import com.yuroyami.syncplay.protocol.sending.Packet
import com.yuroyami.syncplay.screens.adam.Screen
import com.yuroyami.syncplay.screens.adam.Screen.Companion.navigateTo
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.DataStoreKeys.MISC_PLAYER_ENGINE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_FILE_MISMATCH_WARNING
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_PAUSE_ON_SOMEONE_LEAVE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_TLS_ENABLE
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.settings.valueSuspendingly
import com.yuroyami.syncplay.settings.writeValue
import com.yuroyami.syncplay.utils.availablePlatformPlayerEngines
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.utils.instantiateNetworkEngineProtocol
import com.yuroyami.syncplay.utils.iterateDirectory
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.platformCallback
import com.yuroyami.syncplay.utils.timeStamper
import com.yuroyami.syncplay.viewmodel.managers.LifecycleManager
import com.yuroyami.syncplay.viewmodel.managers.OSDManager
import com.yuroyami.syncplay.viewmodel.managers.RoomActionManager
import com.yuroyami.syncplay.viewmodel.managers.RoomCallbackManager
import com.yuroyami.syncplay.viewmodel.managers.SharedPlaylistManager
import com.yuroyami.syncplay.viewmodel.managers.SnackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_attempting_connect
import syncplaymobile.shared.generated.resources.room_attempting_reconnection
import syncplaymobile.shared.generated.resources.room_attempting_tls
import syncplaymobile.shared.generated.resources.room_connected_to_server
import syncplaymobile.shared.generated.resources.room_connection_failed
import syncplaymobile.shared.generated.resources.room_file_mismatch_warning_core
import syncplaymobile.shared.generated.resources.room_file_mismatch_warning_duration
import syncplaymobile.shared.generated.resources.room_file_mismatch_warning_name
import syncplaymobile.shared.generated.resources.room_file_mismatch_warning_size
import syncplaymobile.shared.generated.resources.room_guy_joined
import syncplaymobile.shared.generated.resources.room_guy_left
import syncplaymobile.shared.generated.resources.room_guy_paused
import syncplaymobile.shared.generated.resources.room_guy_played
import syncplaymobile.shared.generated.resources.room_isplayingfile
import syncplaymobile.shared.generated.resources.room_rewinded
import syncplaymobile.shared.generated.resources.room_seeked
import syncplaymobile.shared.generated.resources.room_shared_playlist_changed
import syncplaymobile.shared.generated.resources.room_shared_playlist_no_directories
import syncplaymobile.shared.generated.resources.room_shared_playlist_not_found
import syncplaymobile.shared.generated.resources.room_shared_playlist_updated
import syncplaymobile.shared.generated.resources.room_tls_not_supported
import syncplaymobile.shared.generated.resources.room_tls_supported
import syncplaymobile.shared.generated.resources.room_you_joined_room

class SyncplayViewmodel: ViewModel() {


    val callbackManager: RoomCallbackManager by lazy { RoomCallbackManager(this) }

    val actionManager: RoomActionManager by lazy { RoomActionManager(this) }

    val lifecycleManager: LifecycleManager by lazy { LifecycleManager(this) }

    val playlistManager: SharedPlaylistManager by lazy { SharedPlaylistManager(this) }

    val osdManager: OSDManager by lazy { OSDManager(this) }

    val snackManager: SnackManager by lazy { SnackManager(this) }


    lateinit var nav: NavController

    lateinit var p: SyncplayProtocol

    var player: BasePlayer? = null
    var media: MediaFile? = null

    var wentForFilePick = false

    var setReadyDirectly = false
    val seeks = mutableListOf<Pair<Long, Long>>()

    var hasDoneStartupSlideAnimation = false

    /* Related to playback status */
    val isNowPlaying = mutableStateOf(false)
    val timeFullMs = MutableStateFlow<Long>(0L)
    val timeCurrentMs = MutableStateFlow<Long>(0L)

    val hasVideo = MutableStateFlow(false)
    val hasEnteredPipMode = MutableStateFlow(false)
    val visibleHUD = MutableStateFlow(true)

    var currentTrackChoices: TrackChoices = TrackChoices()



    fun joinRoom(joinConfig: JoinConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            joinConfig.save() //Remembering info

            setReadyDirectly = valueSuspendingly(DataStoreKeys.PREF_READY_FIRST_HAND, true)

            val networkEngine = SyncplayProtocol.getPreferredEngine()
            p = instantiateNetworkEngineProtocol(networkEngine)

            p.session.serverHost = joinConfig.ip.takeIf { it != "syncplay.pl" } ?: "151.80.32.178"
            p.session.serverPort = joinConfig.port
            p.session.currentUsername = joinConfig.user
            p.session.currentRoom = joinConfig.room
            p.session.currentPassword = joinConfig.pw

            launch(Dispatchers.Main) {
                platformCallback.onRoomEnterOrLeave(PlatformCallback.RoomEvent.ENTER)
                nav.navigateTo(Screen.Room)
            }

            val defaultEngine = availablePlatformPlayerEngines.first { it.isDefault }.name //TODO
            val engine = availablePlatformPlayerEngines.first { it.name == valueSuspendingly(MISC_PLAYER_ENGINE, defaultEngine) }
            player = engine.instantiate(this@SyncplayViewmodel)

            /** Connecting (via TLS or noTLS) */
            val tls = valueSuspendingly(PREF_TLS_ENABLE, default = true)
            if (tls && p.supportsTLS()) {
                onTLSCheck()
                p.tls = Constants.TLS.TLS_ASK
            }

            p.connect()
        }
    }

    /*********** Room Utilities *************/

    /** Sends a play/pause playback to the server **/

    /** Mismatches are: Name, Size, Duration. If 3 mismatches are detected, no error is thrown
     * since that would mean that the two files are completely and obviously different.*/
    //TODO/ This needs full refactoring
    fun checkFileMismatches() {
        if (isSoloMode) return

        viewModelScope.launch {
            /** First, we check if user wanna be notified about file mismatchings */
            val pref = valueBlockingly(PREF_FILE_MISMATCH_WARNING, true)

            if (!pref) return@launch

            for (user in p.session.userList.value) {
                val theirFile = user.file ?: continue /* If they have no file, iterate unto next */

                val nameMismatch = (media?.fileName != theirFile.fileName) and (media?.fileNameHashed != theirFile.fileNameHashed)
                val durationMismatch = media?.fileDuration != theirFile.fileDuration
                val sizeMismatch = (media?.fileSize != theirFile.fileSize) and (media?.fileSizeHashed != theirFile.fileSizeHashed)

                if (nameMismatch && durationMismatch && sizeMismatch) continue /* 2 mismatches or less */


                var warning = getString(Res.string.room_file_mismatch_warning_core, user.name)

                if (nameMismatch) warning += getString(Res.string.room_file_mismatch_warning_name)
                if (durationMismatch) warning += getString(Res.string.room_file_mismatch_warning_duration)
                if (sizeMismatch) warning += getString(Res.string.room_file_mismatch_warning_size)

                broadcastMessage(message = { warning }, isChat = false, isError = true)
            }
        }
    }

    /********* Playlist Utils **************/



    /** Tells us whether we're in solo mode to deactivate some online components */
    val isSoloMode: Boolean
        get() = nav.currentBackStackEntry?.destination?.route == Screen.SoloMode.label


    /** End of viewmodel */
    override fun onCleared() {
        super.onCleared()
    }
}