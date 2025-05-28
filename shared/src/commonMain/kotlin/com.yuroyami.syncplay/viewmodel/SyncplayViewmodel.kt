@file:Suppress("DeferredResultUnused")
package com.yuroyami.syncplay.viewmodel

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableLongStateOf
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
import com.yuroyami.syncplay.screens.room.dispatchOSD
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_FILE_MISMATCH_WARNING
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_PAUSE_ON_SOMEONE_LEAVE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_TLS_ENABLE
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.settings.valueSuspendingly
import com.yuroyami.syncplay.settings.writeValue
import com.yuroyami.syncplay.ui.LifecycleWatchdog
import com.yuroyami.syncplay.utils.getDefaultEngine
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.utils.instantiateNetworkEngineProtocol
import com.yuroyami.syncplay.utils.instantiatePlayer
import com.yuroyami.syncplay.utils.iterateDirectory
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.platformCallback
import com.yuroyami.syncplay.utils.timeStamper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

class SyncplayViewmodel: ViewModel(), ProtocolCallback {
    lateinit var nav: NavController

    lateinit var p: SyncplayProtocol

    val isSoloMode = false

    var player: BasePlayer? = null
    var media: MediaFile? = null

    var wentForFilePick = false

    var setReadyDirectly = false
    val seeks = mutableListOf<Pair<Long, Long>>()

    var startupSlide = false

    /* Related to playback status */
    val isNowPlaying = mutableStateOf(false)
    val timeFull = mutableLongStateOf(0L)
    val timeCurrent = mutableLongStateOf(0L)

    val hasVideoG = mutableStateOf(false)
    val hudVisibilityState = mutableStateOf(true)
    val pipMode = mutableStateOf(false)

    var currentTrackChoices: TrackChoices = TrackChoices()

    var playerTrackerJob: Job? = null

    //TODO Lifecycle Stuff

    var background = false
    var lifecycleWatchdog = object: LifecycleWatchdog {

        override fun onResume() {
            background = false
        }

        override fun onStop() {
            if (!pipMode.value) {
                background = true
                player?.pause()
            }
        }

        override fun onCreate() {}

        override fun onStart() {
            background = false
        }

        override fun onPause() {}
    }


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

            val engine = BasePlayer.ENGINE.valueOf(valueSuspendingly(DataStoreKeys.MISC_PLAYER_ENGINE, getDefaultEngine()))
            player = instantiatePlayer(engine)

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
    fun sendPlayback(play: Boolean) {
        if (isSoloMode) return

        p.send<Packet.State> {
            serverTime = null
            doSeek = null
            position = 0
            changeState = 1
            this.play = play
        }
    }

    fun sendSeek(newpos: Long) {
        if (isSoloMode) return

        player?.playerScopeMain?.launch {
            p.send<Packet.State> {
                serverTime = null
                doSeek = true
                position = newpos
                changeState = 1
                this.play = player?.isPlaying() == true
            }
        }
    }

    /** Sends a chat message to the server **/
    fun sendMessage(msg: String) {
        if (isSoloMode) return

        p.send<Packet.Chat> {
            message = msg
        }
    }

    /** This broadcasts a message to show it in the chat section **/
    fun broadcastMessage(isChat: Boolean, chatter: String = "", isError: Boolean = false, message: suspend () -> String) {
        if (isSoloMode) return

        viewModelScope.launch {
            /** Messages are just a wrapper class for everything we need about a message
            So first, we initialize it, customize it, then add it to our long list of messages */
            val msg = Message(
                sender = if (isChat) chatter else null,
                isMainUser = chatter == p.session.currentUsername,
                content = message.invoke(),
                isError = isError
            )

            /** Adding the message instance to our message sequence **/
            withContext(Dispatchers.Main) {
                p.session.messageSequence.add(msg)
            }
        }
    }

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

    /** Video Player Utils */

    /** This pauses playback on the main (necessary) thread **/
    fun pausePlayback() {
        if (background == true) return
        player?.pause()
        platformCallback.onPlayback(true)
    }

    /** This resumes playback on the main thread, and hides system UI **/
    fun playPlayback() {
        if (background == true) return
        player?.play()
        platformCallback.onPlayback(false)
    }

    fun seekBckwd() {
        player?.playerScopeIO?.launch {
            val dec = valueSuspendingly(DataStoreKeys.PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP, 10)

            val currentMs =
                withContext(Dispatchers.Main) { player!!.currentPositionMs() }
            var newPos = ((currentMs) - (dec * 1000L)).coerceIn(
                0, media?.fileDuration?.toLong()?.times(1000L) ?: 0
            )

            if (newPos < 0) {
                newPos = 0
            }

            sendSeek(newPos)
            player?.seekTo(newPos)

            if (isSoloMode) {
                seeks.add(Pair(currentMs, newPos * 1000))
            }
        }
    }

    fun seekFrwrd() {
        player?.playerScopeIO?.launch {
            val inc = valueSuspendingly(DataStoreKeys.PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP, 10)

            val currentMs =
                withContext(Dispatchers.Main) { player!!.currentPositionMs() }
            val newPos = ((currentMs) + (inc * 1000L)).coerceIn(
                0,
                media?.fileDuration?.toLong()?.times(1000L) ?: 0
            )

            sendSeek(newPos)
            player?.seekTo(newPos)

            if (isSoloMode) {
                seeks.add(Pair((currentMs), newPos * 1000))
            }
        }
    }

    /** Tracks progress CONTINUOUSLY and updates it to UI (and server, if no solo mode) */
    fun trackProgress(intervalMillis: Long) {
        //TODO: Don't keep job reference like this
        if (playerTrackerJob == null) {
            playerTrackerJob = viewModelScope.launch(Dispatchers.IO) {
                while (true) {
                    if (player?.isSeekable() == true) {
                        val progress = (player?.currentPositionMs()?.div(1000L)) ?: 0L

                        /* Informing UI */
                        timeCurrent.longValue = progress
                    }
                    delay(intervalMillis)
                }
            }
        }
    }

    /********* Playlist Utils **************/

    /** Shuffles the current playlist and sends it to the server.
     * @param mode False to shuffle all playlist, True to shuffle only the remaining non-played items in queue.*/
    suspend fun shuffle(mode: Boolean) {
        /* If the shared playlist is empty, do nothing */
        if (p.session.spIndex.intValue < 0 || p.session.sharedPlaylist.isEmpty()) return


        /* Shuffling as per the mode selected: False = shuffle all, True = Shuffle rest */
        if (mode) {
            /* Shuffling the rest of playlist is a bit trickier, we split the shared playlist into two
             * grp1 is gonna be the group that doesn't change (everything until current index)
             * grp2 is the group to be shuffled since it's the 'remaining group' */

            val grp1 = p.session.sharedPlaylist.take(p.session.spIndex.intValue + 1).toMutableList()
            val grp2 = p.session.sharedPlaylist.takeLast(p.session.sharedPlaylist.size - grp1.size).shuffled()
            grp1.addAll(grp2)
            p.session.sharedPlaylist.clear()
            p.session.sharedPlaylist.addAll(grp1)
        } else {
            /* Shuffling everything is easy as Kotlin gives us the 'shuffle()' method */
            p.session.sharedPlaylist.shuffle()

            /* Index won't change, but the file at the given index did change, play it */
            retrieveFile(p.session.sharedPlaylist[p.session.spIndex.intValue])
        }

        /* Announcing a new updated list to the room members */
        p.send<Packet.PlaylistChange> {
            files = p.session.sharedPlaylist
        }
    }

    /** Adds URLs from the url adding popup */
    fun addURLs(string: List<String>) {
        val l = mutableListOf<String>()
        l.addAll(p.session.sharedPlaylist)
        for (s in string) {
            if (!p.session.sharedPlaylist.contains(s)) l.add(s)
        }
        p.send<Packet.PlaylistChange> {
            files = l
        }
    }

    /** Adding a file to the playlist: This basically adds one file name to the playlist, then,
     * adds the parent directory to the known media directories, after that, it informs the server
     * about it. The server will send back the new playlist which will invoke playlist updating */
    fun addFiles(uris: List<String>) {
        for (uri in uris) {
            /* We get the file name */
            val filename = getFileName(uri) ?: return

            /* If the playlist already contains this file name, prevent adding it */
            if (p.session.sharedPlaylist.contains(filename)) return

            /* If there is no duplicate, then we proceed, we check if the list is empty */
            if (p.session.sharedPlaylist.isEmpty() && p.session.spIndex.intValue == -1) {
                player?.injectVideo(uri, true)

                p.send<Packet.PlaylistIndex> {
                    index = 0
                }
                //TODO MAKE NON=ASYNC
            }
            p.session.sharedPlaylist.add(filename)
        }
        p.send<Packet.PlaylistChange> {
            files = p.session.sharedPlaylist
        }
    }


    /** Clears the shared playlist */
    fun clearPlaylist() {
        if (p.session.sharedPlaylist.isEmpty()) return

        p.send<Packet.PlaylistChange> {
            files = emptyList()
        }
    }

    /** This will delete an item from playlist at a given index 'i' */
    fun deleteItemFromPlaylist(i: Int) {
        p.session.sharedPlaylist.removeAt(i)
        p.send<Packet.PlaylistChange> {
            files = p.session.sharedPlaylist
        }

        if (p.session.sharedPlaylist.isEmpty()) {
            p.session.spIndex.intValue = -1
        }
    }

    /** This is to send a playlist selection change to the server.
     * This occurs when a user selects a different item from the shared playlist. */
    fun sendPlaylistSelection(i: Int) {
        p.send<Packet.PlaylistIndex> {
            index = i
        }
    }

    /** This is to change playlist selection in response other users' selection */
    suspend fun changePlaylistSelection(index: Int) {
        if (p.session.sharedPlaylist.size < (index + 1)) return /* In rare cases when this was called on an empty list */
        if (index != p.session.spIndex.intValue) {
            /* If the file on that index isn't playing, play the file */
            retrieveFile(p.session.sharedPlaylist[index])
        }
    }


    /****************************************************************************/

    /** Convenient method to add a folder path to the current set of media directories */
    suspend fun saveFolderPathAsMediaDirectory(uri: String) {
        val paths = valueBlockingly(DataStoreKeys.PREF_SP_MEDIA_DIRS, emptySet<String>()).toMutableSet()

        if (!paths.contains(uri)) paths.add(uri)

        writeValue(DataStoreKeys.PREF_SP_MEDIA_DIRS, paths.toSet())
    }

    /**
     * name and load it into ExoPlayer. This is executed on a separate thread since the IO operation
     * is heavy.
     */
    suspend fun retrieveFile(fileName: String) {
        /* We have to know whether the file name is an URL or just a file name */
        if (fileName.contains("http://", true) ||
            fileName.contains("https://", true) ||
            fileName.contains("ftp://", true)
        ) {
            player?.injectVideo(fileName, isUrl = true)
        } else {
            /* We search our media directories which were added by the user in settings */
            val paths = valueBlockingly(DataStoreKeys.PREF_SP_MEDIA_DIRS, emptySet<String>())

            if (paths.isEmpty()) {
                broadcastMessage(message = { getString(Res.string.room_shared_playlist_no_directories) }, isChat = false)
            }

            var fileUri2Play: String? = null

            /* We iterate through the media directory paths spreading their children tree **/
            for (path in paths) {
                iterateDirectory(uri = path, target = fileName) {
                    fileUri2Play  = it

                    /* Loading the file into our player **/
                    player?.injectVideo(fileUri2Play)
                }
            }
            if (fileUri2Play == null) {
                if (media?.fileName != fileName) {
                    val s = getString(Res.string.room_shared_playlist_not_found)
                    coroutineScope {
                        dispatchOSD { s }
                    }
                    broadcastMessage(message = { s }, isChat = false)
                }
            }

        }
    }

    /** Protocol Callback */
    override fun onSomeonePaused(pauser: String) {
        loggy("SYNCPLAY Protocol: Someone ($pauser) paused.", 1001)

        if (pauser != p.session.currentUsername) {
            pausePlayback()
        }

        broadcastMessage(
            message = { getString(Res.string.room_guy_paused, pauser, timeStamper(p.globalPosition)) },
            isChat = false
        )
    }

    override fun onSomeonePlayed(player: String) {
        loggy("SYNCPLAY Protocol: Someone ($player) unpaused.", 1002)

        if (player != p.session.currentUsername) {
            playPlayback()
        }

        broadcastMessage(message = { getString(Res.string.room_guy_played, player) }, isChat = false)
    }

    override fun onChatReceived(chatter: String, chatmessage: String) {
        loggy("SYNCPLAY Protocol: $chatter sent: $chatmessage", 1003)

        broadcastMessage(message = { chatmessage }, isChat = true, chatter = chatter)
    }

    override fun onSomeoneJoined(joiner: String) {
        loggy("SYNCPLAY Protocol: $joiner joined the room.", 1004)

        broadcastMessage(message = { getString(Res.string.room_guy_joined, joiner) }, isChat = false)
    }

    override fun onSomeoneLeft(leaver: String) {
        loggy("SYNCPLAY Protocol: $leaver left the room.", 1005)

        broadcastMessage(message = { getString(Res.string.room_guy_left,leaver) }, isChat = false)

        /* If the setting is enabled, pause playback **/
        if (player?.hasMedia() == true) {
            val pauseOnLeft = valueBlockingly(PREF_PAUSE_ON_SOMEONE_LEAVE, true)
            if (pauseOnLeft) {
                pausePlayback()
            }
        }

        /* Rare cases where a user can see his own self disconnected */
        if (leaver == p.session.currentUsername) {
            onDisconnected()
        }
    }

    override fun onSomeoneSeeked(seeker: String, toPosition: Long) {
        loggy("SYNCPLAY Protocol: $seeker seeked to: $toPosition", 1006)

        val oldPos = p.globalPosition
        val newPos = toPosition

        /* Saving seek so it can be undone on mistake */
        seeks.add(Pair(oldPos * 1000, newPos * 1000))

        broadcastMessage(message = { getString(Res.string.room_seeked,seeker, timeStamper(oldPos), timeStamper(newPos)) }, isChat = false)

        if (seeker != p.session.currentUsername) {
            player?.seekTo((toPosition * 1000.0).toLong())
        }
    }

    override fun onSomeoneBehind(behinder: String, toPosition: Long) {
        loggy("SYNCPLAY Protocol: $behinder is behind. Rewinding to $toPosition", 1007)

        player?.seekTo(toPosition * 1000L)

        broadcastMessage(message = { getString(Res.string.room_rewinded,behinder) }, isChat = false)
    }

    override fun onReceivedList() {
        loggy("SYNCPLAY Protocol: Received list update.", 1008)

    }

    override fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
        loggy("SYNCPLAY Protocol: $person loaded: $file - Duration: $fileduration", 1009)

        broadcastMessage(
            message = { getString(Res.string.room_isplayingfile,
                person,
                file ?: "",
                timeStamper(fileduration?.toLong() ?: 0)
            ) },
            isChat = false
        )

        checkFileMismatches()
    }

    override fun onPlaylistUpdated(user: String) {
        loggy("SYNCPLAY Protocol: Playlist updated by $user", 1010)

        /** Selecting first item on list **/
        if (p.session.sharedPlaylist.isNotEmpty() && p.session.spIndex.intValue == -1) {
            //changePlaylistSelection(0)
        }

        /** Telling user that the playlist has been updated/changed **/
        if (user == "") return
        broadcastMessage(message = { getString(Res.string.room_shared_playlist_updated,user) }, isChat = false)
    }

    override fun onPlaylistIndexChanged(user: String, index: Int) {
        loggy("SYNCPLAY Protocol: Playlist index changed by $user to $index", 1011)

        /** Changing the selection for the user, to load the file at the given index **/
        viewModelScope.launch {
            changePlaylistSelection(index)
        }

        /** Telling user that the playlist selection/index has been changed **/
        if (user == "") return
        broadcastMessage(message = { getString(Res.string.room_shared_playlist_changed,user) }, isChat = false)
    }

    override suspend fun onConnected() {
        loggy("SYNCPLAY Protocol: Connected!", 1012)

        /** Adjusting connection state */
        p.state = Constants.CONNECTIONSTATE.STATE_CONNECTED

        /** Set as ready first-hand */
        if (media == null) {
            p.ready = setReadyDirectly
            p.send<Packet.Readiness> {
                this.isReady = setReadyDirectly
                manuallyInitiated = false
            }
        }


        /** Telling user that they're connected **/
        broadcastMessage(message = { getString(Res.string.room_connected_to_server) }, isChat = false)

        /** Telling user which room they joined **/
        broadcastMessage(message = { getString(Res.string.room_you_joined_room,p.session.currentRoom) }, isChat = false)

        /** Resubmit any ongoing file being played **/
        if (media != null) {
            p.send<Packet.File> {
                this@send.media = this@SyncplayViewmodel.media
            }.await()
        }

        /** Pass any messages that have been pending due to disconnection, then clear the queue */
        for (m in p.session.outboundQueue) {
            p.transmitPacket(m)

        }
        p.session.outboundQueue.clear()
    }

    override fun onConnectionAttempt() {
        loggy("SYNCPLAY Protocol: Attempting connection...", 1013)

        /** Telling user that a connection attempt is on **/
        broadcastMessage(
            message =
                { getString(Res.string.room_attempting_connect,
                    if (p.session.serverHost == "151.80.32.178") "syncplay.pl" else p.session.serverHost,
                    p.session.serverPort.toString()
                ) },
            isChat = false
        )
    }

    override fun onConnectionFailed() {
        loggy("SYNCPLAY Protocol: Connection failed :/", 1014)

        /** Adjusting connection state */
        p.state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED

        /** Telling user that connection has failed **/
        broadcastMessage(
            message = { getString(Res.string.room_connection_failed) },
            isChat = false, isError = true
        )

        /** Attempting reconnection **/
        p.reconnect()
    }

    override fun onDisconnected() {
        loggy("SYNCPLAY Protocol: Disconnected.", 1015)


        /** Adjusting connection state */
        p.state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED

        /** Telling user that the connection has been lost **/
        broadcastMessage(message = { getString(Res.string.room_attempting_reconnection) }, isChat = false, isError = true)

        /** Attempting reconnection **/
        p.reconnect()
    }

    override fun onTLSCheck() {
        loggy("SYNCPLAY Protocol: Checking TLS...", 1016)

        /** Telling user that the app is checking whether the chosen server supports TLS **/
        broadcastMessage(message = { getString(Res.string.room_attempting_tls) }, isChat = false)
    }

    override suspend fun onReceivedTLS(supported: Boolean) {
        loggy("SYNCPLAY Protocol: Received TLS...", 1017)

        /** Deciding next step based on whether the server supports TLS or not **/
        if (supported) {
            broadcastMessage(message = { getString(Res.string.room_tls_supported) }, isChat = false)
            p.tls = Constants.TLS.TLS_YES
            p.upgradeTls()
        } else {
            broadcastMessage(message = { getString(Res.string.room_tls_not_supported) }, isChat = false, isError = true)
            p.tls = Constants.TLS.TLS_NO
        }

        p.send<Packet.Hello> {
            username = p.session.currentUsername
            roomname = p.session.currentRoom
            serverPassword = p.session.currentPassword
        }
    }

    /**************** OTHER ***************/
    var snack = SnackbarHostState()
    fun snackIt(string: String, abruptly: Boolean = true) {
        viewModelScope.launch(Dispatchers.Main) {
            if (abruptly) {
                snack.currentSnackbarData?.dismiss()
            }
            snack.showSnackbar(
                message = string,
                duration = SnackbarDuration.Short
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}