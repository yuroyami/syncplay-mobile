package com.cosmik.syncplay.room

import androidx.lifecycle.ViewModel

class RoomViewModel: ViewModel() {
    var ready = false
    var joinedRoom: Boolean = false
    var currentVideoPosition: Double = 0.0
    var currentVideoName: String = ""
    var currentVideoLength: Double = 0.0
    var currentVideoSize: Int = 0
    var serverHost: String = "151.80.32.178"
    var serverPort: Int = 8999
    var connected = false
    var currentUsername: String = "username_${(0..999999999999).random()}"
    var currentRoom: String = "roomname"
    var rewindThreshold = 12L
    //User Properties: User Index // Readiness // File Name // File Duration // File Size
    var userList: MutableMap<String, MutableList<String>> = mutableMapOf()
    var messageSequence: MutableList<String> = mutableListOf()
}