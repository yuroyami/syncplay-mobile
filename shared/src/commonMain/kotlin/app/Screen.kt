package app

import androidx.navigation3.runtime.NavKey
import app.home.JoinConfig
import app.theme.SaveableTheme
import kotlinx.serialization.Serializable

/**
 * The screens of the app. Each screen is one entry of the navigation back stack.
 */
sealed interface Screen : NavKey {

    /**
     * The home screen, with the form to join a room.
     */
    @Serializable
    data object Home : Screen

    /**
     * The room screen. A room is the group of people who watch together.
     *
     * @property joinConfig The details to join the room with, or null for solo mode (offline
     *   playback).
     */
    @Serializable
    data class Room(val joinConfig: JoinConfig?) : Screen

    /**
     * The theme editor. [themeToEdit] is the custom theme to edit, or null to start a new theme
     * from the current one.
     */
    @Serializable
    data class ThemeCreator(val themeToEdit: SaveableTheme? = null) : Screen

    /** Global settings. [categoryKey] opens one category directly (a deep link). */
    @Serializable
    data class Settings(val categoryKey: String? = null) : Screen
}