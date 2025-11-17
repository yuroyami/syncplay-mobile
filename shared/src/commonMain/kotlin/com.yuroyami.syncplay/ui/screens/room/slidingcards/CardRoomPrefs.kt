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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.managers.settings.SETTINGS_ROOM
import com.yuroyami.syncplay.managers.settings.SettingCategory
import com.yuroyami.syncplay.managers.settings.SettingsUI
import com.yuroyami.syncplay.managers.settings.SettingsUI.SettingsGrid
import com.yuroyami.syncplay.ui.components.FlexibleIcon
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.jostFont
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.screens.home.SettingGridState
import com.yuroyami.syncplay.ui.theme.Theming.flexibleGradient
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_card_title_in_room_prefs

object CardRoomPrefs {

    @Composable
    fun InRoomSettingsCard() {
        val settingState = remember { mutableStateOf(SettingGridState.NAVIGATING_CATEGORIES) }
        val viewmodel = LocalRoomViewmodel.current

        var roomSettings: List<SettingCategory>? by remember { mutableStateOf(null) }

        LaunchedEffect(null) {
            val commonSettings = SETTINGS_ROOM.toMutableList()
            viewmodel.player.configurableSettings()?.let { playerSpecificSettings ->
                commonSettings.add(commonSettings.size - 2, playerSpecificSettings)
            }
            roomSettings = commonSettings
        }

        roomSettings?.let { settings ->
            Card(
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(width = Dp.Hairline, brush = Brush.linearGradient(colors = flexibleGradient)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(0.5f)),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    FlexibleText(
                        modifier = Modifier.align(TopCenter).padding(6.dp),
                        text = stringResource(Res.string.room_card_title_in_room_prefs),
                        fillingColors = flexibleGradient,
                        size = 17f,
                        font = jostFont
                    )

                    if (settingState.value == SettingGridState.INSIDE_CATEGORY) {
                        FlexibleIcon(
                            modifier = Modifier.align(TopEnd).padding(6.dp),
                            icon = Icons.AutoMirrored.Filled.Redo, size = 32, shadowColors = listOf(Color.DarkGray),
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