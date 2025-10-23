package com.yuroyami.syncplay.ui.screens.adam

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Screen : NavKey {
    @Serializable
    data object Home : Screen
    @Serializable
    data object Room : Screen
    @Serializable
    data object SoloMode : Screen
}