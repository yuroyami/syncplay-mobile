package app.room.ui.rightcards

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.TopCenter
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.home.components.SettingGridState
import app.preferences.settings.SETTINGS_ROOM
import app.preferences.settings.SettingCategory
import app.preferences.settings.SettingsUI
import app.preferences.settings.SettingsUI.SettingsGrid
import app.uicomponents.FlexibleIcon
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
            val uiOpacity by viewmodel.uiState.uiOpacity.collectAsState()
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(uiOpacity)),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        modifier = Modifier.align(TopCenter).padding(8.dp),
                        text = stringResource(Res.string.room_card_title_in_room_prefs),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                    )

                    if (settingState.value == SettingGridState.INSIDE_CATEGORY) {
                        FlexibleIcon(
                            modifier = Modifier.align(TopEnd).padding(6.dp),
                            icon = Icons.AutoMirrored.Filled.Redo, size = 32,
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