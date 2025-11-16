package com.yuroyami.syncplay.ui.screens.room.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.core.ScrollArea
import com.composables.core.Thumb
import com.composables.core.VerticalScrollbar
import com.composables.core.rememberScrollAreaState
import com.yuroyami.syncplay.ui.components.FlexibleAnnotatedText
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.SyncplayPopup
import com.yuroyami.syncplay.ui.components.helveticaFont
import com.yuroyami.syncplay.ui.components.syncplayFont
import com.yuroyami.syncplay.ui.screens.adam.LocalChatPalette
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.theme.Theming
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.close


@Composable
fun ChatHistoryPopup() {
    val viewmodel = LocalRoomViewmodel.current
    val scope = rememberCoroutineScope()

    val msgs = remember { viewmodel.session.messageSequence }
    val palette = LocalChatPalette.current.copy(includeTimestamp = true) // We always show timestamp

    val visible by viewmodel.uiManager.popupChatHistory.collectAsState()

    return SyncplayPopup(
        dialogOpen = visible,
        widthPercent = 0.7f,
        heightPercent = 0.9f,
        strokeWidth = 0.5f,
        alpha = 0.9f,
        onDismiss = { viewmodel.uiManager.popupChatHistory.value = false }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {

            /* The title */
            FlexibleText(
                modifier = Modifier.padding(6.dp),
                text = "Chat History", //TODO Localize
                shadowColors = listOf(Color.Black),
                fillingColors = Theming.SP_GRADIENT,
                size = 18f,
                font = syncplayFont
            )

            /* The actual messages */
            val lazyListState: LazyListState = rememberLazyListState()
            val scrollAreaState = rememberScrollAreaState(lazyListState)

            //TODO Fix inaccurate scrollbar by giving ScrollArea an absolute height based on screenheight

            ScrollArea(
                state = scrollAreaState,
                modifier = Modifier.weight(1f)
            ) {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize().background(Color(50, 50, 50, 50))
                ) {
                    items(msgs) { msg ->
                        FlexibleAnnotatedText(
                            modifier = Modifier.fillMaxWidth(),
                            text = msg.factorize(palette),
                            size = 10f,
                            font = helveticaFont,
                            lineHeight = 14f,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight().width(4.dp)
                ) {
                    Thumb(
                        modifier = Modifier.background(Color.Gray)
                    )
                }
            }

            LaunchedEffect(null) {
                scope.launch {
                    lazyListState.scrollToItem(msgs.size - 1)
                }
            }

            /* Exit button */
            Button(
                modifier = Modifier.padding(8.dp),
                onClick = {
                    viewmodel.uiManager.popupChatHistory.value = false
                },
            ) {
                Icon(imageVector = Icons.Filled.Close, "")
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Res.string.close), fontSize = 14.sp)
            }
        }
    }
}