package com.yuroyami.syncplay.ui.screens.adam

import androidx.navigation3.runtime.NavKey
import com.yuroyami.syncplay.models.JoinConfig
import kotlinx.serialization.Serializable

sealed interface Screen : NavKey {
    @Serializable
    data object Home : Screen

    @Serializable
    data class Room(val joinConfig: JoinConfig?) : Screen
}