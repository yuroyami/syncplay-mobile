package com.yuroyami.syncplay.models

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.flow.MutableStateFlow

/*************************************************************************************************
 * Session wrapper class. It encapsulates all information and data we need about a session.
 *
 * In summary, a session is the status of the room, for example, the users in the room, properties
 * of the room (name, server, password, features...etc), as well as the shared playlist if there is
 * one or if it is activated in the first place.
 *
 * A protocol always exists with one session at a time. If a session changes (for example, changing
 * a room) then the session is overwritten and the protocol relaunches.
 ************************************************************************************************/

class Session {

    /** Variables related to joining info */
    var serverHost: String = "151.80.32.178"
    var serverPort: Int = 8997
    var currentUsername: String = "Anonymous${(1000..9999).random()}"
    var currentRoom: String = "roomname"
    var currentPassword: String = ""

    /** Variable that stores all users that exist within the room */
    var userList = MutableStateFlow(listOf<User>())

    /** Variable that stores all messages that have been sent/received */
    var messageSequence = mutableStateListOf<Message>()

    /** Outbound messages queue (When the connection is lost):
     *   This basically works like a waiting queue that stacks outgoing messages (JSONs)
     *   during disconnections, then it will be iterated-through and then cleared. */
    var outboundQueue = mutableListOf<String>()

    /** Variable that stores the shared playlist for the session */
    var sharedPlaylist = mutableStateListOf<String>() /* List of files */
    var spIndex = mutableIntStateOf(-1) /* Index of the currently playing file for the session */

//    /** A list of media directories to look for shared playlist file names */
//    var mediaDirectories = mutableListOf<String>()

}