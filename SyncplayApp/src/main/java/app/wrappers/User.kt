package app.wrappers

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**************************************************************************************
 * User wrapper class. It encapsulates all information and data we need about a user  *
 **************************************************************************************/

/** We ought to use a data class (cuz it enforces the use of primary constructor and delegates some object-oriented functions)
 *  because if we don't, Jetpack Compose won't receive any observed events for value/property change */
data class User(
    var index: Int = 0,
    var name: String = "",
    var readiness: MutableState<Boolean?> = mutableStateOf(null),
    var file: MutableState<MediaFile?> = mutableStateOf(null),
)