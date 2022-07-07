package com.chromaticnoob.syncplayutils.utils

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
    var currentPassword: String? = null

    /** Variable that stores all users that exist within the room */
    var userList: MutableList<User> = mutableListOf()

    /** Variable that stores all messages that have been sent/received */
    var messageSequence: MutableList<Message> = mutableListOf()

    /** Variable that stores the shared playlist for the session */
    var sharedPlaylist = mutableListOf<String>()

}