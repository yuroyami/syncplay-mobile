package com.yuroyami.syncplay.ui.screens.adam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import com.yuroyami.syncplay.logic.settings.SettingStyling
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.ui.screens.home.HomeScreenUI
import com.yuroyami.syncplay.ui.screens.room.RoomScreenUI
import com.yuroyami.syncplay.ui.screens.room.tabs.CardController
import com.yuroyami.syncplay.ui.theme.AppTheme
import com.yuroyami.syncplay.ui.utils.messagePalette
import io.github.composefluent.FluentTheme

/******
 * This is called the AdamScreen mainly because it is the root/parent composable.
 * It takes care of hosting the upper and initial states, and also takes care of navigating from
 * screen to another.
 */

val LocalNavigator = compositionLocalOf<NavController> { error("No Navigator provided yet") }
val LocalViewmodel = compositionLocalOf<SyncplayViewmodel> { error("No Viewmodel provided yet") }
val LocalScreen = compositionLocalOf<Screen> { error("No Screen provided") }
val LocalSettingStyling = staticCompositionLocalOf<SettingStyling> { error("No Setting Styling provided") }
val LocalChatPalette = compositionLocalOf<MessagePalette> { error("No Chat Palette provided") }
val LocalCardController = compositionLocalOf<CardController> { error("No CardController provided yet") }


@Composable
fun AdamScreen(onViewmodelReady: (SyncplayViewmodel) -> Unit) {
    val viewmodel = viewModel<SyncplayViewmodel>()
    val navigator = rememberNavController()
    val navEntry by navigator.currentBackStackEntryAsState()

    val currentScreen by remember { derivedStateOf { Screen.fromLabel(navEntry?.destination?.route) } }

    LaunchedEffect(viewmodel) {
        onViewmodelReady.invoke(viewmodel)
    }

    LaunchedEffect(navigator) {
        viewmodel.uiManager.nav = navigator
    }

    AppTheme {
        CompositionLocalProvider(
            LocalViewmodel provides viewmodel,
            LocalNavigator provides navigator,
            LocalScreen provides currentScreen,
            LocalChatPalette provides messagePalette.value
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