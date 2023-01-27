package app.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import app.R
import app.ui.Paletting
import app.utils.ComposeUtils.FancyText2
import app.utils.ComposeUtils.RoomPopup
import com.godaddy.android.colorpicker.ClassicColorPicker
import com.godaddy.android.colorpicker.HsvColor

object ColorPickingPopup {

    @Composable
    fun ComponentActivity.ColorPickingPopup(visibilityState: MutableState<Boolean>, multiple: Boolean = false) {
        return RoomPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.5f,
            heightPercent = 0.85f,
            strokeWidth = 0.5f,
            cardBackgroundColor = Color.DarkGray,
            onDismiss = { visibilityState.value = false }
        ) {
            val context = LocalContext.current

            ConstraintLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {

                val (title, picker, buttons) = createRefs()

                /* The title */
                FancyText2(
                    modifier = Modifier.constrainAs(title) {
                        top.linkTo(parent.top, 12.dp)
                        end.linkTo(parent.end)
                        start.linkTo(parent.start)
                    },
                    string = stringResource(R.string.media_directories),
                    solid = Color.Black,
                    size = 18f,
                    font = Font(R.font.directive4bold)
                )

                /* The card that holds the color picker */
                Card(
                    modifier = Modifier
                        .constrainAs(picker) {
                            top.linkTo(title.bottom, 8.dp)
                            absoluteLeft.linkTo(parent.absoluteLeft)
                            absoluteRight.linkTo(parent.absoluteRight)
                            bottom.linkTo(buttons.top, 12.dp)
                            width = Dimension.percent(0.9f)
                            height = Dimension.fillToConstraints
                        },
                    shape = RoundedCornerShape(size = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.LightGray)
                ) {
                    ClassicColorPicker(
                        modifier = Modifier.fillMaxSize(),
                        color = HsvColor.from(Color.Black),
                        onColorChanged = { color: HsvColor ->
                            // Do something with the color
                        }
                    )
                }

                /* Exit button */
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Paletting.OLD_SP_YELLOW),
                    border = BorderStroke(width = 1.dp, color = Color.Black),
                    modifier = Modifier.constrainAs(buttons) {
                        bottom.linkTo(parent.bottom, 4.dp)
                        end.linkTo(parent.end, 12.dp)
                        start.linkTo(parent.start, 12.dp)
                        width = Dimension.wrapContent
                    },
                    onClick = {
                        visibilityState.value = false
                    },
                ) {
                    Icon(imageVector = Icons.Filled.Done, "", tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.media_directories_clear_all), fontSize = 14.sp, color = Color.Black)
                }
            }
        }
    }
}