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
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.yuroyami.syncplay.managers.settings.SettingStyling
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.ui.components.messagePalette
import com.yuroyami.syncplay.ui.screens.Screen
import com.yuroyami.syncplay.ui.screens.home.HomeScreenUI
import com.yuroyami.syncplay.ui.screens.room.RoomScreenUI
import com.yuroyami.syncplay.ui.screens.room.tabs.CardController
import com.yuroyami.syncplay.ui.screens.theme.SaveableTheme
import com.yuroyami.syncplay.ui.screens.theme.ThemeCreatorScreenUI
import com.yuroyami.syncplay.utils.createWeakRef
import com.yuroyami.syncplay.viewmodels.HomeViewmodel
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import com.yuroyami.syncplay.viewmodels.SyncplayViewmodel

/** Provides access to the global [SyncplayViewmodel] instance shared across the app. */
val LocalGlobalViewmodel = compositionLocalOf<SyncplayViewmodel> { error("No Viewmodel provided yet") }

/** Provides access to the current [RoomViewmodel] within the room screen scope. */
val LocalRoomViewmodel = compositionLocalOf<RoomViewmodel> { error("No Viewmodel provided yet") }

/** Provides access to the current [SaveableTheme] across the app composable scope. */
val LocalTheme = compositionLocalOf<SaveableTheme> { error("No theme provided yet") }

/** Provides access to the currently active [com.yuroyami.syncplay.ui.screens.Screen] in the navigation back stack. */
val LocalScreen = compositionLocalOf<Screen?> { error("No Screen provided") }

/** Provides access to the current [SettingStyling] configuration for the app. */
val LocalSettingStyling = staticCompositionLocalOf<SettingStyling> { error("No Setting Styling provided") }

/** Provides access to the current [MessagePalette] for chat message color theming. */
val LocalChatPalette = compositionLocalOf<MessagePalette> { error("No Chat Palette provided") }

/** Provides access to the current [CardController] instance for managing UI card behavior. */
val LocalCardController = compositionLocalOf<CardController> { error("No CardController provided yet") }

/**
 * The root composable for the app.
 *
 * This composable initializes the global [SyncplayViewmodel], sets up the main
 * navigation back stack, and provides key CompositionLocals such as theme,
 * view models, and chat palette.
 *
 * It acts as the parent container for all screens and handles navigation
 * between them, using [NavDisplay] for composable screen transitions.
 *
 * @see HomeScreenUI
 * @see RoomScreenUI
 */
@Composable
fun AdamScreen(onGlobalViewmodel: (SyncplayViewmodel) -> Unit) {
    val globalviewmodel = viewModel(
        key = "global_viewmodel",
        modelClass = SyncplayViewmodel::class,
        factory = viewModelFactory { initializer { SyncplayViewmodel() } }
    )

    LaunchedEffect(null) {
        onGlobalViewmodel(globalviewmodel)
    }

    val backstack = remember { globalviewmodel.backstack }

    val currentScreen by remember { derivedStateOf { backstack.lastOrNull() } }
    val currentTheme by globalviewmodel.themeManager.currentTheme.collectAsState()

    CompositionLocalProvider(
        LocalGlobalViewmodel provides globalviewmodel,
        LocalScreen provides currentScreen,
        LocalChatPalette provides messagePalette.value,
        LocalTheme provides currentTheme
    ) {
        MaterialTheme(
            colorScheme = currentTheme.dynamicScheme
        ) {
            NavDisplay(
                backStack = backstack,
                onBack = {},
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = entryProvider {
                    entry<Screen.Home> {
                        val viewmodel = viewModel(
                            key = "home_viewmodel",
                            modelClass = HomeViewmodel::class,
                            factory = viewModelFactory { initializer { HomeViewmodel(backStack = globalviewmodel.backstack) } }
                        )

                        HomeScreenUI(viewmodel)
                    }

                    entry<Screen.Room> { room ->
                        val viewmodel = viewModel(
                            key = "room_viewmodel",
                            modelClass = RoomViewmodel::class,
                            factory = viewModelFactory {
                                initializer { RoomViewmodel(joinConfig = room.joinConfig, backStack = globalviewmodel.backstack) }
                            }
                        )

                        LaunchedEffect(null) {
                            globalviewmodel.roomWeakRef = createWeakRef(viewmodel)
                        }

                        CompositionLocalProvider(
                            LocalRoomViewmodel provides viewmodel
                        ) {
                            RoomScreenUI(viewmodel)
                        }
                    }

                    entry<Screen.ThemeCreator> { themeCreator ->
                        ThemeCreatorScreenUI(
                            themeToEdit = themeCreator.themeToEdit
                        )
                    }
                }
            )
        }
    }
}
