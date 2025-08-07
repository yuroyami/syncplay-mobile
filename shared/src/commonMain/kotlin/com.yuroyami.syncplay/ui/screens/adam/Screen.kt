package com.yuroyami.syncplay.screens.adam

import androidx.navigation.NavController

sealed class Screen(val label: String) {
    data object Home : Screen("home")
    data object Room : Screen("room")
    data object SoloMode : Screen("solo_room")

    companion object {
        fun fromLabel(label: String?): Screen = when (label) {
            Home.label -> Home
            Room.label -> Room
            SoloMode.label -> SoloMode
            else -> Home
        }

        fun NavController.navigateTo(screen: Screen, noReturn: Boolean = true) {
            navigate(screen.label) {
                if (noReturn) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }
}