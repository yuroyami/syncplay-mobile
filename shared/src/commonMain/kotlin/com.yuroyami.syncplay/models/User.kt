package com.yuroyami.syncplay.models

/**************************************************************************************
 * User wrapper class. It encapsulates all information and data we need about a user  *
 **************************************************************************************/
data class User(
    var index: Int = 0, //The index of the user, as declared by the server
    var name: String = "", //The name that the user announced to the group
    var readiness: Boolean, //Whether the user marked themself ready or not
    var file: MediaFile?, //The file info which the user is playing, null if none
)