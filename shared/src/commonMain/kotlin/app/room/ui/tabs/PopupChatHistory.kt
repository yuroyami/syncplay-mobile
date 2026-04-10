package app.room.ui.tabs

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
import app.LocalChatPalette
import app.LocalRoomViewmodel
import app.theme.Theming.flexibleGradient
import app.uicomponents.FlexibleAnnotatedText
import app.uicomponents.FlexibleText
import app.uicomponents.SyncplayPopup
import app.uicomponents.helveticaFont
import app.uicomponents.jostFont
import com.composables.core.ScrollArea
import com.composables.core.Thumb
import com.composables.core.VerticalScrollbar
import com.composables.core.rememberScrollAreaState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.close
import syncplaymobile.shared.generated.resources.room_overflow_msghistory


@Composable
fun ChatHistoryPopup() {
    val viewmodel = LocalRoomViewmodel.current
    val scope = rememberCoroutineScope()

    val msgs by viewmodel.session.messageSequence.collectAsState()
    val palette = LocalChatPalette.current.copy(includeTimestamp = true) // We always show timestamp

    val visible by viewmodel.uiState.popupChatHistory.collectAsState()

    return SyncplayPopup(
        dialogOpen = visible,
        widthPercent = 0.7f,
        heightPercent = 0.9f,
        strokeWidth = 0.5f,
        alpha = 0.9f,
        onDismiss = { viewmodel.uiState.popupChatHistory.value = false }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {

            /* The title */
            FlexibleText(
                modifier = Modifier.padding(6.dp),
                text = stringResource(Res.string.room_overflow_msghistory),
                shadowColors = listOf(Color.Black),
                fillingColors = flexibleGradient,
                size = 17f,
                font = jostFont
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
                    contentPadding = PaddingValues(0.dp),
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
                    viewmodel.uiState.popupChatHistory.value = false
                },
            ) {
                Icon(imageVector = Icons.Filled.Close, "")
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Res.string.close), fontSize = 14.sp)
            }
        }
    }
}