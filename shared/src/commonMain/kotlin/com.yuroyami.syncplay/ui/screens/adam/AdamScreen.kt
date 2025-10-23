package com.yuroyami.syncplay.ui.screens.adam

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import com.yuroyami.syncplay.logic.settings.SettingStyling
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.ui.screens.home.HomeScreenUI
import com.yuroyami.syncplay.ui.screens.room.RoomScreenUI
import com.yuroyami.syncplay.ui.screens.room.tabs.CardController
import com.yuroyami.syncplay.ui.utils.messagePalette

/******
 * This is called the AdamScreen mainly because it is the root/parent composable.
 * It takes care of hosting the upper and initial states, and also takes care of navigating from
 * screen to another.
 */

val LocalViewmodel = compositionLocalOf<SyncplayViewmodel> { error("No Viewmodel provided yet") }
val LocalScreen = compositionLocalOf<Screen?> { error("No Screen provided") }
val LocalSettingStyling = staticCompositionLocalOf<SettingStyling> { error("No Setting Styling provided") }
val LocalChatPalette = compositionLocalOf<MessagePalette> { error("No Chat Palette provided") }
val LocalCardController = compositionLocalOf<CardController> { error("No CardController provided yet") }

@Composable
fun AdamScreen(onViewmodelReady: (SyncplayViewmodel) -> Unit) {
    val viewmodel = viewModel<SyncplayViewmodel>()
    val backstack = remember { viewmodel.uiManager.backStack }
    val currentScreen by remember { derivedStateOf { backstack.lastOrNull() } }

    LaunchedEffect(viewmodel) {
        onViewmodelReady.invoke(viewmodel)
    }

    val currentTheme by viewmodel.themeManager.currentTheme.collectAsState()

    MaterialTheme(
        colorScheme = currentTheme.scheme
    ) {
        CompositionLocalProvider(
            LocalViewmodel provides viewmodel,
            LocalScreen provides currentScreen,
            LocalChatPalette provides messagePalette.value
        ) {
            NavDisplay(
                backStack = viewmodel.uiManager.backStack,
                entryProvider = entryProvider {
                    entry<Screen.Home> {
                        HomeScreenUI()
                    }
                    entry<Screen.Room> {
                        RoomScreenUI()
                    }
                    entry<Screen.SoloMode> {
                        RoomScreenUI()
                    }
                }
            )
        }
    }
}