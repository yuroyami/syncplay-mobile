package com.yuroyami.syncplay.managers.protocol

import com.yuroyami.syncplay.managers.protocol.handler.Set
import com.yuroyami.syncplay.managers.protocol.handler.Set.ControllerAuthResponse

/**
 * Callback interface for handling Syncplay protocol events.
 *
 * Defines all possible events that can occur during a Syncplay session, from playback
 * control changes to connection state updates. Implementations (typically [com.yuroyami.syncplay.managers.OnRoomEventManager])
 * respond to these events by updating UI, triggering local playback changes, or notifying users.
 */
interface ProtocolCallback {
    /**
     * Called when any user pauses playback.
     *
     * @param pauser Username of the user who paused
     */
    fun onSomeonePaused(pauser: String)

    /**
     * Called when any user resumes playback.
     *
     * @param player Username of the user who resumed
     */
    fun onSomeonePlayed(player: String)

    /**
     * Called when a chat message is received.
     *
     * @param chatter Username of the message sender
     * @param chatmessage The message content
     */
    fun onChatReceived(chatter: String, chatmessage: String)

    /**
     * Called when a user joins the room.
     *
     * @param joiner Username of the user who joined
     */
    fun onSomeoneJoined(joiner: String)

    /**
     * Called when a user leaves the room.
     *
     * @param leaver Username of the user who left
     */
    fun onSomeoneLeft(leaver: String)

    /**
     * Called when a user seeks to a different position.
     *
     * @param seeker Username of the user who seeked
     * @param toPosition The new position in seconds
     */
    fun onSomeoneSeeked(seeker: String, toPosition: Double)

    /**
     * Called when a user is detected as behind and playback is rewound to sync.
     *
     * @param behinder Username of the user who was behind
     * @param toPosition The position in seconds to rewind to
     */
    fun onSomeoneBehind(behinder: String, toPosition: Double)

    /**
     * Called when the server sends an updated user list.
     */
    fun onReceivedList()

    /**
     * Called when a user loads a new media file.
     *
     * @param person Username of the user who loaded the file
     * @param file Filename or path, if available
     * @param fileduration File duration in seconds, if available
     */
    fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?)

    /**
     * Called when the shared playlist is updated (files added/removed).
     *
     * @param user Username of the user who updated the playlist (empty for server updates)
     */
    fun onPlaylistUpdated(user: String)

    /**
     * Called when the shared playlist's selected index changes.
     *
     * @param user Username of the user who changed the index (empty for server changes)
     * @param index The new playlist index (0-based)
     */
    fun onPlaylistIndexChanged(user: String, index: Int)

    /**
     * Called when successfully connected to the Syncplay server.
     *
     * Suspends to allow for async initialization like sending initial packets.
     */
    suspend fun onConnected()

    /**
     * Called when beginning a connection attempt to the server.
     */
    fun onConnectionAttempt()

    /**
     * Called when a connection attempt fails.
     */
    fun onConnectionFailed()

    /**
     * Called when disconnected from the server after being connected.
     */
    fun onDisconnected()

    /**
     * Called when checking if the server supports TLS encryption.
     */
    fun onTLSCheck()

    /**
     * Called when the server responds to the TLS support check.
     *
     * @param supported Whether the server supports TLS encryption
     */
    suspend fun onReceivedTLS(supported: Boolean)

    /**
     * Called when a new controlled (managed) room is created.
     *
     * Provides the room name and operator password for the newly created room.
     *
     * @param data Information about the newly created controlled room
     */
    fun onNewControlledRoom(data: Set.NewControlledRoom)

    /**
     * Called after a user attempts to authenticate as a room operator (controller).
     *
     * @param data object containing information all info about the attempt
     */
    fun onHandleControllerAuth(data: ControllerAuthResponse)
}