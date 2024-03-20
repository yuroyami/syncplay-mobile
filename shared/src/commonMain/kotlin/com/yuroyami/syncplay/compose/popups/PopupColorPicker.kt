package com.yuroyami.syncplay.compose.popups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.SpaceEvenly
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.compose.ComposeUtils.FancyText2
import com.yuroyami.syncplay.compose.ComposeUtils.RoomPopup
import com.yuroyami.syncplay.lyricist.rememberStrings
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.utils.colorpicker.ClassicColorPicker
import com.yuroyami.syncplay.utils.colorpicker.HsvColor
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Res
import kotlin.math.ceil
import syncplaymobile.shared.generated.resources.*

object PopupColorPicker {

    @Composable
    fun ColorPickingPopup(
        visibilityState: MutableState<Boolean>,
        initialColor: HsvColor = HsvColor.from(Color.Black),
        onColorChanged: (HsvColor) -> Unit,
        onDefaultReset: () -> Unit
    ) {
        return RoomPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.7f,
            heightPercent = 0.85f,
            strokeWidth = 0.5f,
            cardBackgroundColor = Color.DarkGray,
            onDismiss = { visibilityState.value = false }
        ) {
            val color = remember { mutableStateOf(initialColor) }

            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {

                /* The title */
                FancyText2(
                    modifier = Modifier.weight(1f).padding(6.dp),
                    string = "Pick a Color",
                    solid = Color.Black,
                    size = 18f,
                    font = Font(Res.font.Directive4_Regular)
                )

                /* The card that holds the color picker */
                ClassicColorPicker(
                    modifier = Modifier.fillMaxWidth(0.8f).weight(3f).padding(6.dp),
                    color = initialColor,
                    onColorChanged = { clr: HsvColor ->
                        color.value = clr
                        onColorChanged(clr)
                    }
                )

                /* Exit button */
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = SpaceEvenly,
                    verticalAlignment = CenterVertically
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f).padding(6.dp),
                        onClick = onDefaultReset
                    ) {
                        Text("Reset to default", fontSize = 10.sp)
                    }

                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Paletting.OLD_SP_YELLOW),
                        border = BorderStroke(width = 1.dp, color = Color.Black),
                        modifier = Modifier.weight(1f).padding(6.dp),
                        onClick = {
                            visibilityState.value = false
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.Done, "", tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        val localz = rememberStrings()
                        Text(localz.strings.done, fontSize = 14.sp, color = Color.Black)
                    }

                    Surface(
                        color = color.value.toColor(),
                        border = BorderStroke(0.5.dp, Color.Gray),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(0.5f).padding(6.dp)
                    ) {
                        Canvas(
                            modifier = Modifier.fillMaxSize().alpha(1.0f - color.value.alpha)
                        ) {
                            clipRect {
                                val darkColor = Color.LightGray
                                val lightColor = Color.White

                                val gridSizePx = 6.dp.toPx()
                                val cellCountX = ceil(this.size.width / gridSizePx).toInt()
                                val cellCountY = ceil(this.size.height / gridSizePx).toInt()
                                for (i in 0 until cellCountX) {
                                    for (j in 0 until cellCountY) {
                                        val checkeredcolor = if ((i + j) % 2 == 0) darkColor else lightColor

                                        val x = i * gridSizePx
                                        val y = j * gridSizePx
                                        drawRect(checkeredcolor, Offset(x, y), Size(gridSizePx, gridSizePx))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}