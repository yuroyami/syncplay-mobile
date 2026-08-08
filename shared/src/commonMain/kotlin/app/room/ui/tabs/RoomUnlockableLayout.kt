package app.room.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.theme.Theming
import app.theme.Theming.flexibleGradient
import app.uicomponents.gradientOverlay
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun RoomUnlockableLayout() {
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalRoomUiState.current

    val lockedMode by cardController.tabLock.collectAsState()
    val isInPipMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()
    val isChatSupported by viewmodel.protocol.supportsChat.collectAsState()

    if (lockedMode) {
        var unlockButtonVisibility by remember { mutableStateOf(true) }
        var chatDraft by remember { mutableStateOf("") }
        val keyboardFocusRequester = remember { FocusRequester() }
        val softwareKeyboardController = LocalSoftwareKeyboardController.current
        val chatEnabled = isChatSupported && !viewmodel.isSoloMode
        val maxChatLength = viewmodel.session.roomFeatures.maxChatMessageLength.coerceAtLeast(1)

        LaunchedEffect(chatEnabled) {
            if (chatEnabled) {
                softwareKeyboardController?.hide()
                runCatching { keyboardFocusRequester.requestFocus() }
            }
        }

        Box(
            Modifier.fillMaxSize()
                .padding(top = 10.dp, end = 74.dp)
                .focusRequester(keyboardFocusRequester)
                .lockedChatKeyboardInput(
                    enabled = chatEnabled,
                    onText = { text ->
                        chatDraft = appendLockedChatText(chatDraft, text, maxChatLength)
                    },
                    onBackspace = {
                        chatDraft = removeLockedChatCharacter(chatDraft)
                    },
                    onSend = {
                        lockedChatMessageToSend(chatDraft, maxChatLength)?.let {
                            viewmodel.dispatcher.sendMessage(it)
                        }
                        chatDraft = ""
                    },
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        unlockButtonVisibility = !unlockButtonVisibility
                    }
            )
        ) {
            if (chatDraft.isNotEmpty()) {
                Text(
                    text = chatDraft,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .fillMaxWidth(0.75f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.DarkGray.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            /** Unlock Card */
            if (unlockButtonVisibility && !isInPipMode) {
                LaunchedEffect(null) {
                    delay(2200.milliseconds)
                    unlockButtonVisibility = false
                }

                Card(
                    modifier = Modifier
                        .alpha(0.5f)
                        .width(64.dp).aspectRatio(1f)
                        .align(Alignment.TopEnd)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = Theming.SP_ORANGE)
                        ) {
                            cardController.tabLock.value = false
                            viewmodel.uiState.visibleHUD.value = true
                        },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.linearGradient(colors = flexibleGradient)
                    ),
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Filled.NoEncryption,
                            contentDescription = "",
                            modifier = Modifier.size(32.dp).align(Alignment.Center)
                                .gradientOverlay()
                        )
                    }
                }
            }
        }
    }
}
