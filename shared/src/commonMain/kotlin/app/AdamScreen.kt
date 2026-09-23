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

/** The app-wide [SyncplayViewmodel]. */
val LocalGlobalViewmodel = compositionLocalOf<SyncplayViewmodel> { error("No Viewmodel provided yet") }

/** The [RoomViewmodel] of the open room. Only the room screen provides it. */
val LocalRoomViewmodel = compositionLocalOf<RoomViewmodel> { error("No Viewmodel provided yet") }

/** The current [SaveableTheme]. */
val LocalTheme = compositionLocalOf<SaveableTheme> { error("No theme provided yet") }

/** The current [Screen]: the last entry of the navigation back stack. */
val LocalScreen = compositionLocalOf<Screen?> { error("No Screen provided") }

/** The [MessagePalette]: the colours of chat messages. */
val LocalChatPalette = compositionLocalOf<MessagePalette> { error("No Chat Palette provided") }

val LocalRoomUiState = compositionLocalOf<RoomUiStateManager> { error("No RoomUiState provided yet") }

/**
 * The root composable of the app.
 *
 * It creates the global [SyncplayViewmodel] and passes it once to [onGlobalViewmodel], so the
 * platform host can keep it. It provides the app-wide composition locals (theme, palette, type
 * roles, chat palette, view models) and shows the last screen of the back stack with [NavDisplay].
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

    // The design tokens: the type roles come from the one font family, the palette from the theme.
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
        /* Applies the display language before anything below reads a string, so the first frame
         * already uses it. A language change updates the whole app without a restart. */
        val savedLanguage by DISPLAY_LANG.watchPref()
        remember(savedLanguage) { Localization.apply(savedLanguage) }

        /* The layout stays left to right in every language, Arabic included. Only the words
         * change. The direction is pinned here so that an Arabic device does not mirror the app. */
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ProvideAppStrings(Localization.lyricist) {
        // Every page sits on the theme's ground colour, so the window colour never shows through.
        Box(Modifier.fillMaxSize().background(designPalette.ground)) {
            GlassBackdrop {
                // Reduced motion is on when the app's switch or the platform setting is on. The
                // platform setting is read again only when the switch changes.
                LaunchedEffect(Unit) {
                    REDUCE_MOTION.flow().collect { Motion.reduced = it || reducedMotion() }
                }
                val slidePx = with(LocalDensity.current) { 24.dp.roundToPx() }

                NavDisplay(
                backStack = backstack,
                onBack = {
                    /* In the room, Back closes one open layer at a time, and asks to leave only
                     * when nothing is open. On a TV remote, Back is the only way out of a panel. */
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
 * The page transition. A push slides the new page 24dp in from the right and fades it in, and a
 * pop does the reverse. Entering or leaving the room is a mode change, so it only crossfades.
 * With reduced motion, every transition is instant.
 */
private fun AnimatedContentTransitionScope<Scene<Screen>>.pageTransition(pop: Boolean, slidePx: Int): ContentTransform {
    val toRoom = targetState.entries.lastOrNull()?.contentKey is Screen.Room
    val fromRoom = initialState.entries.lastOrNull()?.contentKey is Screen.Room
    if (Motion.reduced || toRoom || fromRoom) return fadeIn(Motion.move()) togetherWith fadeOut(Motion.move())
    val direction = if (pop) -1 else 1
    return (fadeIn(Motion.move()) + slideInHorizontally(Motion.move()) { direction * slidePx }) togetherWith
        (fadeOut(Motion.move()) + slideOutHorizontally(Motion.move()) { -direction * slidePx })
}
