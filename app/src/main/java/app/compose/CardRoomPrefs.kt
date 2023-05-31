package app.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import app.R
import app.activities.WatchActivity
import app.datastore.MySettings.inRoomPreferences
import app.settings.SettingsUI
import app.settings.SettingsUI.SettingsGrid
import app.ui.Paletting
import app.utils.ComposeUtils.FancyIcon2
import app.utils.ComposeUtils.FancyText2

object CardRoomPrefs {

    @Composable
    fun WatchActivity.InRoomSettingsCard() {
        val settingState = remember { mutableIntStateOf(1) }

        Card(
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        ) {
            ConstraintLayout(modifier = Modifier.fillMaxSize()) {
                val (title, settings, navbutton) = createRefs()

                FancyText2(
                    modifier = Modifier
                        .constrainAs(title) {
                            top.linkTo(parent.top, 6.dp)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        }
                        .padding(6.dp),
                    string = "In-room Preferences",
                    solid = Color.Transparent,
                    size = 16f,
                    font = Font(R.font.directive4bold)
                )

                if (settingState.intValue == 2) {
                    FancyIcon2(
                        icon = Icons.Filled.Redo, size = 32, shadowColor = Color.DarkGray,
                        onClick = { settingState.intValue = 1 },
                        modifier = Modifier.constrainAs(navbutton) {
                            top.linkTo(title.top)
                            bottom.linkTo(title.bottom)
                            absoluteRight.linkTo(parent.absoluteRight)
                            absoluteLeft.linkTo(title.absoluteRight)
                        })
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .constrainAs(settings) {
                            top.linkTo(title.bottom, 6.dp)
                            start.linkTo(parent.start, 2.dp)
                            end.linkTo(parent.end, 2.dp)
                            bottom.linkTo(parent.bottom)
                            height = Dimension.fillToConstraints
                            width = Dimension.fillToConstraints
                        }) {
                    SettingsGrid(
                        modifier = Modifier
                            .fillMaxHeight()
                            .wrapContentWidth(),
                        settingcategories = inRoomPreferences(),
                        state = settingState,
                        layoutOrientation = SettingsUI.SettingsGridLayout.SETTINGS_GRID_HORIZONTAL_GRID,
                        titleSize = 9f,
                        cardSize = 48f,
                        onCardClicked = {
                            settingState.intValue = 2
                        }
                    )
                }
            }
        }
    }
}