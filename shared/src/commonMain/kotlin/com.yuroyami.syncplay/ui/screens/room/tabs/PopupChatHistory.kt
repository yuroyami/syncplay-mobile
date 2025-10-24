package com.yuroyami.syncplay.ui.screens.room.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.ui.screens.adam.LocalChatPalette
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.utils.FancyText2
import com.yuroyami.syncplay.ui.utils.FlexibleFancyAnnotatedText
import com.yuroyami.syncplay.ui.utils.SyncplayPopup
import com.yuroyami.syncplay.ui.utils.drawVerticalScrollbar
import com.yuroyami.syncplay.ui.utils.getRegularFont
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.close

object PopupChatHistory {
    @Composable
    fun ChatHistoryPopup(visibilityState: MutableState<Boolean>) {
        val viewmodel = LocalRoomViewmodel.current
        val scope = rememberCoroutineScope()

        val msgs = remember { viewmodel.session.messageSequence }
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
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
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
//                val lazyListState: LazyListState = rememberLazyListState()
//                val scrollAreaState = rememberScrollAreaState(lazyListState)
//
//                ScrollArea(
//                    state = scrollAreaState,
//                    modifier = Modifier.weight(1f)
//                ) {
//
//                    LazyColumn(
//                        state = lazyListState,
//                        contentPadding = PaddingValues(8.dp),
//                        modifier = Modifier.fillMaxSize().background(Color(50, 50, 50, 50))
//                    ) {
//                        items(msgs) { msg ->
//                            repeat(5) {
//                                FlexibleFancyAnnotatedText(
//                                    modifier = Modifier.fillMaxWidth(),
//                                    text = msg.factorize(palette),
//                                    size = 10f,
//                                    font = getRegularFont(),
//                                    lineHeight = 14.sp,
//                                    overflow = TextOverflow.Ellipsis,
//                                )
//                            }
//                        }
//                    }
//
//                    VerticalScrollbar(
//                        modifier = Modifier
//                            .align(Alignment.CenterEnd)
//                            .fillMaxHeight().width(4.dp)
//                    ) {
//                        Thumb(
//                            modifier = Modifier.background(Color.Gray),
//                            thumbVisibility = ThumbVisibility.HideWhileIdle(fadeIn(), fadeOut(), hideDelay = 50.milliseconds)
//                        )
//                    }
//                }

                val lazyListState: LazyListState = rememberLazyListState()
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.weight(1f).background(Color(50, 50, 50, 50)).drawVerticalScrollbar(lazyListState).clipToBounds()
                ) {
                    items(msgs) { msg ->
                        repeat(5) {
                            FlexibleFancyAnnotatedText(
                                modifier = Modifier.fillMaxWidth(),
                                text = msg.factorize(palette),
                                size = 10f,
                                font = getRegularFont(),
                                lineHeight = 14.sp,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                SideEffect {
                    scope.launch {
                        lazyListState.scrollToItem(msgs.size - 1)
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