package com.yuroyami.syncplay.screens.adam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.screens.home.HomeScreenUI
import com.yuroyami.syncplay.screens.room.RoomComposables.ComposedMessagePalette
import com.yuroyami.syncplay.screens.room.RoomScreenUI
import com.yuroyami.syncplay.settings.SettingStyling
import com.yuroyami.syncplay.ui.AppTheme
import com.yuroyami.syncplay.utils.ScreenSizeInfo
import com.yuroyami.syncplay.viewmodel.SyncplayViewmodel

/******
 * This is called the AdamScreen mainly because it is the root/parent composable.
 * It takes care of hosting the upper and initial states, and also takes care of navigating from
 * screen to another.
 */

val LocalNavigator = compositionLocalOf<NavController> { error("No Navigator provided yet") }
val LocalViewmodel = compositionLocalOf<SyncplayViewmodel> { error("No Viewmodel provided yet") }
val LocalScreenSize = compositionLocalOf<ScreenSizeInfo> { error("No Screen Size Info provided") }
val LocalScreen = compositionLocalOf<Screen> { error("No Screen provided") }
val LocalSettingStyling = staticCompositionLocalOf<SettingStyling> { error("No Setting Styling provided") }
val LocalChatPalette = compositionLocalOf<MessagePalette> { error("No Chat Palette provided") }

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

@Composable
fun AdamScreen(onViewmodelReady: (SyncplayViewmodel) -> Unit) {

    val scope = rememberCoroutineScope()
    val viewmodel = viewModel<SyncplayViewmodel>()
    val navigator = rememberNavController()
    val navEntry by navigator.currentBackStackEntryAsState()
    val haptic = LocalHapticFeedback.current
    val windowInfo = LocalWindowInfo.current.containerSize
    val screenSizeInfo = remember(windowInfo) {
        ScreenSizeInfo(
            heightPx = windowInfo.height,
            widthPx = windowInfo.width
        )
    }

    val currentScreen by remember {
        derivedStateOf {
            Screen.fromLabel(navEntry?.destination?.route)
        }
    }

    LaunchedEffect(viewmodel) {
        onViewmodelReady.invoke(viewmodel)
    }
    LaunchedEffect(navigator) {
        viewmodel.nav = navigator
    }

    AppTheme {
        CompositionLocalProvider(
            LocalViewmodel provides viewmodel,
            LocalNavigator provides navigator,
            LocalScreenSize provides screenSizeInfo,
            LocalScreen provides currentScreen,
            LocalChatPalette provides ComposedMessagePalette()
        ) {
            NavHost(
                navController = navigator,
                startDestination = Screen.Home.label
            ) {
                composable(Screen.Home.label) { HomeScreenUI() }
                composable(Screen.Room.label) { RoomScreenUI() }
                composable(Screen.SoloMode.label) { RoomScreenUI() }
            }
        }
    }
}