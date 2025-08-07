package com.yuroyami.syncplay.components.popups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.kborowy.colorpicker.KolorPicker
import com.yuroyami.syncplay.components.FancyText2
import com.yuroyami.syncplay.components.SyncplayPopup
import com.yuroyami.syncplay.ui.Paletting
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.done
import kotlin.math.ceil

object PopupColorPicker {

    @Composable
    fun ColorPickingPopup(
        visibilityState: MutableState<Boolean>,
        initialColor: Color = Color.Black,
        onColorChanged: (Color) -> Unit,
        onDefaultReset: () -> Unit
    ) {
        return SyncplayPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.7f,
            heightPercent = 0.85f,
            strokeWidth = 0.5f,
            onDismiss = { visibilityState.value = false }
        ) {
            var color by remember { mutableStateOf(initialColor) }

            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = CenterHorizontally,
                verticalArrangement = SpaceEvenly
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
                KolorPicker(
                    modifier = Modifier.fillMaxWidth(0.8f).weight(3f).padding(6.dp),
                    initialColor = initialColor,
                    onColorSelected = { clr ->
                        color = clr
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
                        Text(stringResource(Res.string.done), fontSize = 14.sp, color = Color.Black)
                    }

                    Surface(
                        color = color,
                        border = BorderStroke(0.5.dp, Color.Gray),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(0.5f).padding(6.dp)
                    ) {
                        Canvas(
                            modifier = Modifier.fillMaxSize().alpha(1.0f - color.alpha)
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