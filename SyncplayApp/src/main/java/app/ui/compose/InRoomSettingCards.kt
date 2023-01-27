package app.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.dp
import app.R
import app.datastore.MySettings.inRoomPreferences
import app.settings.SettingsUI
import app.settings.SettingsUI.SettingsGrid
import app.ui.Paletting
import app.ui.activities.WatchActivity
import app.utils.ComposeUtils.FancyText2

object InRoomSettingCards {

    @Composable
    fun WatchActivity.InRoomSettingsCard() {
        val settingState = remember { mutableStateOf(1) }

        Card(
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)),
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        ) {
            FancyText2(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(6.dp),
                string = "In-room Preferences",
                solid = Color.Transparent,
                size = 16f,
                font = Font(R.font.directive4bold)
            )

            SettingsGrid(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterHorizontally),
                settingcategories = inRoomPreferences(),
                state = settingState,
                layoutOrientation = SettingsUI.SettingsGridLayout.SETTINGS_GRID_VERTICAL,
                titleSize = 9f,
                cardSize = 48f,
                onCardClicked = {
                    settingState.value = 2
                }
            )
        }
    }
}