package com.yuroyami.syncplay.ui.screens.adam

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.yuroyami.syncplay.managers.settings.SettingStyling
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.ui.screens.home.HomeScreenUI
import com.yuroyami.syncplay.ui.screens.room.RoomScreenUI
import com.yuroyami.syncplay.ui.screens.room.tabs.CardController
import com.yuroyami.syncplay.ui.utils.messagePalette
import com.yuroyami.syncplay.viewmodels.HomeViewmodel
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import com.yuroyami.syncplay.viewmodels.SyncplayViewmodel

/******
 * This is called the AdamScreen mainly because it is the root/parent composable.
 * It takes care of hosting the upper and initial states, and also takes care of navigating from
 * screen to another.
 */

val LocalGlobalViewmodel = compositionLocalOf<SyncplayViewmodel> { error("No Viewmodel provided yet") }
val LocalRoomViewmodel = compositionLocalOf<RoomViewmodel> { error("No Viewmodel provided yet") }
val LocalScreen = compositionLocalOf<Screen?> { error("No Screen provided") }
val LocalSettingStyling = staticCompositionLocalOf<SettingStyling> { error("No Setting Styling provided") }
val LocalChatPalette = compositionLocalOf<MessagePalette> { error("No Chat Palette provided") }
val LocalCardController = compositionLocalOf<CardController> { error("No CardController provided yet") }

@Composable
fun AdamScreen() {
    val viewmodel = viewModel(
        key = "global_viewmodel",
        modelClass = SyncplayViewmodel::class,
        factory = viewModelFactory { initializer { SyncplayViewmodel() } }
    )

    //TODO: Use built-in for state-restorable rememberNavBackStack()
    val backstack = remember { mutableStateListOf<Screen>(Screen.Home) }

    val currentScreen by remember { derivedStateOf { backstack.lastOrNull() } }
    val currentTheme by viewmodel.themeManager.currentTheme.collectAsState()

    MaterialTheme(
        colorScheme = currentTheme.scheme
    ) {
        CompositionLocalProvider(
            LocalGlobalViewmodel provides viewmodel,
            LocalScreen provides currentScreen,
            LocalChatPalette provides messagePalette.value
        ) {
            NavDisplay(
                backStack = backstack,
                onBack = {},
                entryDecorators = listOf(
                    //This allows the view-models created within entry scope to be scoped only for that entry
                    //Meaning that they will be cleared once the entry (screen) leaves composition
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = entryProvider {
                    entry<Screen.Home> {
                        val viewmodel = viewModel(
                            key = "home_viewmodel",
                            modelClass = HomeViewmodel::class,
                            factory = viewModelFactory { initializer { HomeViewmodel(backStack = backstack) } }
                        )

                        HomeScreenUI(viewmodel)
                    }


                    entry<Screen.Room> { room ->
                        val viewmodel = viewModel(
                            key = "room_viewmodel",
                            modelClass = RoomViewmodel::class,
                            factory = viewModelFactory { initializer { RoomViewmodel(joinConfig = room.joinConfig, backStack = backstack) } }
                        )

                        CompositionLocalProvider(
                            LocalRoomViewmodel provides viewmodel
                        ) {
                            RoomScreenUI(viewmodel)
                        }
                    }
                }
            )
        }
    }
}