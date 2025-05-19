package com.yuroyami.syncplay.models

/**************************************************************************************
 * User wrapper class. It encapsulates all information and data we need about a user  *
 **************************************************************************************/

/** Jetpack Compose uses data class built-in comparison functions (such as hashValue or isEqual) to recompose changes */
data class User(
    var index: Int = 0,
    var name: String = "",
    var readiness: Boolean,
    var file: MediaFile?,
)