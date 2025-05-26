package com.yuroyami.syncplay.components.popups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.SpaceBetween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.components.ComposeUtils
import com.yuroyami.syncplay.components.ComposeUtils.FancyText2
import com.yuroyami.syncplay.components.ComposeUtils.SyncplayPopup
import com.yuroyami.syncplay.components.getRegularFont
import com.yuroyami.syncplay.screens.adam.LocalChatPalette
import com.yuroyami.syncplay.screens.adam.LocalViewmodel
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.close

object PopupChatHistory {
    @Composable
    fun ChatHistoryPopup(visibilityState: MutableState<Boolean>) {
        val viewmodel = LocalViewmodel.current
        val msgs = remember { viewmodel.p.session.messageSequence }
        val palette = LocalChatPalette.current.copy(includeTimestamp = true) // We always show timestamp

        return SyncplayPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.7f,
            heightPercent = 0.9f,
            strokeWidth = 0.5f,
            alpha = 0.9f,
            onDismiss = { visibilityState.value = false }
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = CenterHorizontally,
                verticalArrangement = SpaceBetween,
            ) {

                /* The title */
                FancyText2(
                    modifier = Modifier.padding(6.dp),
                    string = "Chat History",
                    solid = Color.Black,
                    size = 18f,
                    font = Font(Res.font.Directive4_Regular)
                )

                /* The actual messages */
                LazyColumn(
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxHeight(0.7f).background(Color(50, 50, 50, 50))
                ) {
                    items(msgs) {
                        ComposeUtils.FlexibleFancyAnnotatedText(
                            modifier = Modifier.fillMaxWidth(),
                            text = it.factorize(palette),
                            size = 10f,
                            font = getRegularFont(),
                            lineHeight = 14.sp,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                /* Exit button */
                Button(
                    modifier = Modifier.padding(8.dp),
                    onClick = {
                        visibilityState.value = false
                    },
                ) {
                    Icon(imageVector = Icons.Filled.Close, "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.close), fontSize = 14.sp)
                }

            }
        }
    }
}