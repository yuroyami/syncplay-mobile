package com.yuroyami.syncplay.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuroyami.syncplay.ui.theme.Theming
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res

/** Creates a multi-choice dialog which is populated by the given list */
@Composable
fun MultiChoiceDialog(
    title: String = "",
    items: Map<String, String>,
    selectedItem: Map.Entry<String, String>,
    onItemClick: (Map.Entry<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = { onDismiss() }) {
        Card(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(size = 10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Gray)
        ) {
            Column(modifier = Modifier.padding(all = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (title != "") {
                    Box(modifier = Modifier.wrapContentWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = title,
                            style = TextStyle(
                                brush = Brush.linearGradient(colors = Theming.SP_GRADIENT),
                                shadow = Shadow(offset = Offset(0f, 0f), blurRadius = 1f),
                                fontFamily = FontFamily(Font(Res.font.Directive4_Regular)),
                                fontSize = (15.6).sp
                            )
                        )
                        Text(
                            text = title,
                            style = TextStyle(
                                color = Color.DarkGray,
                                fontFamily = FontFamily(Font(Res.font.Directive4_Regular)),
                                fontSize = 15.sp,
                            )
                        )
                    }
                }

                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier.padding(all = 16.dp).verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items.entries.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = Theming.SP_ORANGE)
                            ) {
                                onItemClick(item)
                                onDismiss()
                            }
                        ) {
                            RadioButton(
                                selected = item == selectedItem,
                                onClick = {
                                    onItemClick(item)
                                    onDismiss()
                                }
                            )
                            Text(
                                text = item.key, modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}