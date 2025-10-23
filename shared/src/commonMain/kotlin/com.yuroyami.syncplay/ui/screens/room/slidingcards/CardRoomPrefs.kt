package com.yuroyami.syncplay.ui.screens.room.slidingcards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.TopCenter
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.logic.settings.SETTINGS_ROOM
import com.yuroyami.syncplay.logic.settings.SettingCollection
import com.yuroyami.syncplay.logic.settings.SettingsUI
import com.yuroyami.syncplay.logic.settings.SettingsUI.SettingsGrid
import com.yuroyami.syncplay.ui.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.ui.screens.home.SettingGridState
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.ui.utils.FancyIcon2
import com.yuroyami.syncplay.ui.utils.FancyText2
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_card_title_in_room_prefs

object CardRoomPrefs {

    @Composable
    fun InRoomSettingsCard() {
        val settingState = remember { mutableStateOf(SettingGridState.NAVIGATING_CATEGORIES) }
        val viewmodel = LocalViewmodel.current

        var roomSettings: SettingCollection? by remember { mutableStateOf(null) }

        LaunchedEffect(null) {
            val commonSettings = SETTINGS_ROOM.toMutableMap()
            viewmodel.player?.configurableSettings()?.let { playerSpecificSettings ->
                commonSettings.put(playerSpecificSettings.first, playerSpecificSettings.second)
            }
            roomSettings = commonSettings as SettingCollection
        }

        roomSettings?.let { settings ->
            Card(
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Theming.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(0.5f)),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    FancyText2(
                        modifier = Modifier.align(TopCenter).padding(6.dp),
                        string = stringResource(Res.string.room_card_title_in_room_prefs),
                        solid = Color.Transparent,
                        size = 16f,
                        font = Font(Res.font.Directive4_Regular)
                    )

                    if (settingState.value == SettingGridState.INSIDE_CATEGORY) {
                        FancyIcon2(
                            modifier = Modifier.align(TopEnd).padding(6.dp),
                            icon = Icons.AutoMirrored.Filled.Redo, size = 32, shadowColor = Color.DarkGray,
                            onClick = { settingState.value = SettingGridState.NAVIGATING_CATEGORIES }
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize().padding(top = 32.dp).align(TopCenter)
                    ) {
                        SettingsGrid(
                            modifier = Modifier.fillMaxSize(),
                            layout = SettingsUI.Layout.SETTINGS_ROOM,
                            settings = settings,
                            state = settingState,
                            titleSize = 9f,
                            cardSize = 48f,
                        )
                    }
                }
            }
        }
    }
}