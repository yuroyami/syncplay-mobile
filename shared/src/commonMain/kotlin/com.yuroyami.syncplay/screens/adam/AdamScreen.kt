package com.yuroyami.syncplay.screens.adam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuroyami.syncplay.screens.home.HomeScreenUI
import com.yuroyami.syncplay.screens.room.RoomScreenUI
import com.yuroyami.syncplay.utils.ScreenSizeInfo
import com.yuroyami.syncplay.utils.rememberScreenSizeInfo
import com.yuroyami.syncplay.viewmodel.SyncplayViewmodel
import kotlinx.coroutines.Dispatchers

/******
 * This is called the AdamScreen mainly because it is the root/parent composable.
 * It takes care of hosting the upper and initial states, and also takes care of navigating from
 * screen to another.
 */

val LocalNavigator = compositionLocalOf<NavController> { error("No Navigator provided yet") }
val LocalViewmodel = compositionLocalOf<SyncplayViewmodel> { error("No Viewmodel provided yet") }
val LocalScreenSize = compositionLocalOf<ScreenSizeInfo> { error("No Screen Size Info provided") }
val LocalSoloMode = compositionLocalOf<Boolean> { false }

sealed class Screen(val label: String) {
    data object Home : Screen("home")
    data object Room : Screen("room")
    data object SoloMode : Screen("solo_room")
}

@Composable
fun AdamScreen() {
    val scope = rememberCoroutineScope { Dispatchers.Default }
    val haptic = LocalHapticFeedback.current

    /* Create our global long-lived viewmodel */
    val viewmodel = viewModel(
        key = "syncplay_viewmodel",
        modelClass = SyncplayViewmodel::class,
        factory = viewModelFactory { initializer { SyncplayViewmodel() } }
    )

    val navigator = rememberNavController()
    val navEntry by navigator.currentBackStackEntryAsState()

    val screenSizeInfo = rememberScreenSizeInfo()

    CompositionLocalProvider(
        LocalViewmodel provides viewmodel,
        LocalNavigator provides navigator,
        LocalScreenSize provides screenSizeInfo,
    ) {
        NavHost(
            navController = navigator,
            startDestination = Screen.Home.label
        ) {
            with(Screen.Home) {
                composable(label) {
                    HomeScreenUI()
                }
            }

            with(Screen.Room) {
                composable(label) {
                    RoomScreenUI()
                }
            }
            with(Screen.SoloMode) {
                composable(label) {
                    CompositionLocalProvider(
                        LocalSoloMode provides true
                    ) {
                        RoomScreenUI()
                    }
                }
            }
        }
    }
}