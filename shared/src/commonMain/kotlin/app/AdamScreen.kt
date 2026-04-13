package app

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
import app.preferences.LocalPrefsState
import app.preferences.datastoreStateFlow
import app.uicomponents.messagePalette
import app.utils.createWeakRef
import app.home.HomeScreenUI
import app.home.HomeViewmodel
import app.preferences.settings.SettingStyling
import app.room.RoomScreenUI
import app.room.RoomUiStateManager
import app.room.RoomViewmodel
import app.room.models.MessagePalette
import app.server.ServerViewmodel
import app.server.ui.ServerHostScreenUI
import app.theme.SaveableTheme
import app.theme.ThemeCreatorScreenUI

/** Provides access to the global [SyncplayViewmodel] instance shared across the app. */
val LocalGlobalViewmodel = compositionLocalOf<SyncplayViewmodel> { error("No Viewmodel provided yet") }

/** Provides access to the current [RoomViewmodel] within the room screen scope. */
val LocalRoomViewmodel = compositionLocalOf<RoomViewmodel> { error("No Viewmodel provided yet") }

/** Provides access to the current [SaveableTheme] across the app composable scope. */
val LocalTheme = compositionLocalOf<SaveableTheme> { error("No theme provided yet") }

/** Provides access to the currently active [Screen] in the navigation back stack. */
val LocalScreen = compositionLocalOf<Screen?> { error("No Screen provided") }

/** Provides access to the current [SettingStyling] configuration for the app. */
val LocalSettingStyling = staticCompositionLocalOf<SettingStyling> { error("No Setting Styling provided") }

/** Provides access to the current [MessagePalette] for chat message color theming. */
val LocalChatPalette = compositionLocalOf<MessagePalette> { error("No Chat Palette provided") }

val LocalRoomUiState = compositionLocalOf<RoomUiStateManager> { error("No RoomUiState provided yet") }

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
    val currentTheme by globalviewmodel.currentTheme.collectAsState()
    val prefsState = datastoreStateFlow.collectAsState()

    CompositionLocalProvider(
        LocalPrefsState provides prefsState,
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

                    entry<Screen.ServerHost> {
                        val viewmodel = viewModel(
                            key = "server_viewmodel",
                            modelClass = ServerViewmodel::class,
                            factory = viewModelFactory {
                                initializer { ServerViewmodel(backStack = globalviewmodel.backstack) }
                            }
                        )
                        ServerHostScreenUI(viewmodel)
                    }
                }
            )
        }
    }
}
