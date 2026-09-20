package app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.i18n.Localization
import app.i18n.ProvideAppStrings
import app.preferences.LocalPrefsState
import app.preferences.Preferences.DISPLAY_LANG
import app.preferences.watchPref
import app.preferences.datastoreStateFlow
import app.preferences.settings.SettingsScreenUI
import app.uicomponents.GlassBackdrop
import app.uicomponents.messagePalette
import app.utils.createWeakRef
import app.home.HomeScreenUI
import app.home.HomeViewmodel
import app.room.RoomScreenUI
import app.room.RoomUiStateManager
import app.room.RoomViewmodel
import app.room.models.MessagePalette
import androidx.compose.ui.text.font.FontFamily
import app.theme.LocalPalette
import app.theme.LocalSurfacePalette
import app.theme.LocalType
import app.theme.Palette
import app.theme.SaveableTheme
import app.theme.ThemeCreatorScreenUI
import app.theme.TypeRoles
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import app.theme.Motion
import app.uicomponents.LocalIsTelevision
import app.uicomponents.TvSafeArea
import app.uicomponents.LocalWidthClass
import app.uicomponents.currentWidthClass
import app.utils.isTelevision
import app.utils.reducedMotion
import app.utils.get
import app.preferences.Preferences.REDUCE_MOTION
import app.preferences.flow
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.Scene
import app.uicomponents.lexendFont

/** Provides access to the global [SyncplayViewmodel] instance shared across the app. */
val LocalGlobalViewmodel = compositionLocalOf<SyncplayViewmodel> { error("No Viewmodel provided yet") }

/** Provides access to the current [RoomViewmodel] within the room screen scope. */
val LocalRoomViewmodel = compositionLocalOf<RoomViewmodel> { error("No Viewmodel provided yet") }

/** Provides access to the current [SaveableTheme] across the app composable scope. */
val LocalTheme = compositionLocalOf<SaveableTheme> { error("No theme provided yet") }

/** Provides access to the currently active [Screen] in the navigation back stack. */
val LocalScreen = compositionLocalOf<Screen?> { error("No Screen provided") }

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

    // The design tokens: one type binding per family, one palette per theme.
    val lexend = FontFamily(lexendFont)
    val typeRoles = remember(lexend) { TypeRoles.from(lexend) }
    val designPalette = remember(currentTheme) { Palette.from(currentTheme.dynamicScheme, currentTheme) }

    CompositionLocalProvider(
        LocalPrefsState provides prefsState,
        LocalGlobalViewmodel provides globalviewmodel,
        LocalScreen provides currentScreen,
        LocalChatPalette provides messagePalette.value,
        LocalTheme provides currentTheme,
        LocalType provides typeRoles,
        LocalPalette provides designPalette,
        LocalSurfacePalette provides designPalette,
        LocalWidthClass provides currentWidthClass(),
        LocalIsTelevision provides remember { isTelevision() },
    ) {
        /* The display language, applied before anything below reads a string so the first frame
         * is already in the right one. A change moves the whole app with no restart. */
        val savedLanguage by DISPLAY_LANG.watchPref()
        remember(savedLanguage) { Localization.apply(savedLanguage) }

        /* The layout stays left to right in every language, Arabic included: only the words
         * change. Pinned here so an Arabic device does not mirror the app either. */
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ProvideAppStrings(Localization.lyricist) {
        // The ground is the theme's: every page sits on it, so no window colour shows through.
        Box(Modifier.fillMaxSize().background(designPalette.ground)) {
            GlassBackdrop {
                // Reduced motion: the platform setting or the switch, read once per change.
                LaunchedEffect(Unit) {
                    REDUCE_MOTION.flow().collect { Motion.reduced = it || reducedMotion() }
                }
                val slidePx = with(LocalDensity.current) { 24.dp.roundToPx() }

                NavDisplay(
                backStack = backstack,
                onBack = {
                    /* In the room, back closes whatever is open, one layer at a time, and only asks
                     * to leave once nothing is. A remote's Back is its only way out of a panel. */
                    val room = globalviewmodel.roomWeakRef?.get()
                    val ui = room?.uiState
                    when {
                        backstack.lastOrNull() !is Screen.Room || ui == null ->
                            if (backstack.size > 1) backstack.removeAt(backstack.lastIndex)
                        ui.gifPanelVisible.value -> ui.gifPanelVisible.value = false
                        ui.anySidePanelOpen -> ui.closeSidePanels()
                        ui.controlPanel.value -> ui.toggleControlPanel(false)
                        ui.railActionsExpanded.value -> ui.railActionsExpanded.value = false
                        else -> ui.askLeave.value = true
                    }
                },
                transitionSpec = { pageTransition(pop = false, slidePx) },
                popTransitionSpec = { pageTransition(pop = true, slidePx) },
                predictivePopTransitionSpec = { pageTransition(pop = true, slidePx) },
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

                        TvSafeArea { HomeScreenUI(viewmodel) }
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
                        TvSafeArea {
                            ThemeCreatorScreenUI(
                                themeToEdit = themeCreator.themeToEdit
                            )
                        }
                    }

                    entry<Screen.Settings> { settings ->
                        TvSafeArea { SettingsScreenUI(settings.categoryKey) }
                    }

                }
                )
            }
        }
        }
        }
    }
}

/**
 * A push slides 24dp in from the trailing edge and fades; a pop is the reverse. Entering or
 * leaving the room is a mode change, so it crossfades. Reduced motion collapses all of it.
 */
private fun AnimatedContentTransitionScope<Scene<Screen>>.pageTransition(pop: Boolean, slidePx: Int): ContentTransform {
    val toRoom = targetState.entries.lastOrNull()?.contentKey is Screen.Room
    val fromRoom = initialState.entries.lastOrNull()?.contentKey is Screen.Room
    if (Motion.reduced || toRoom || fromRoom) return fadeIn(Motion.move()) togetherWith fadeOut(Motion.move())
    val direction = if (pop) -1 else 1
    return (fadeIn(Motion.move()) + slideInHorizontally(Motion.move()) { direction * slidePx }) togetherWith
        (fadeOut(Motion.move()) + slideOutHorizontally(Motion.move()) { -direction * slidePx })
}
