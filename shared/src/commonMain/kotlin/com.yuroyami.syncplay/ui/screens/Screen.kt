package com.yuroyami.syncplay.ui.screens

import androidx.navigation3.runtime.NavKey
import com.yuroyami.syncplay.models.JoinConfig
import kotlinx.serialization.Serializable

/**
 * Represents the different screens used in the app’s navigation graph.
 */
sealed interface Screen : NavKey {

    /**
     * The home screen of the app.
     */
    @Serializable
    data object Home : Screen

    /**
     * The room screen, shown when a user joins or creates a room.
     *
     * @property joinConfig Optional configuration data used to join an existing room.
     */
    @Serializable
    data class Room(val joinConfig: JoinConfig?) : Screen

    /**
     * The screen where user can customize screens
     *
     */
    @Serializable
    data object ThemeCreator: Screen
}