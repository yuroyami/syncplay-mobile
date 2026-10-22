package app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.room.ui.bottombar.TopContrastUnderlay
import app.room.ui.chat.NewMessagesMarker
import app.theme.Type
import app.uicomponents.controls.Text
import kotlin.test.Test

/** The room's top shade over a white frame, and the chat's new messages marker in every language. */
class RoomOverlaysGolden {

    @Test
    fun `the top row stays readable over a white frame`() {
        DesignHarness.render("room-top-shade", widthDp = 640, heightDp = 200, overVideo = true) {
            Box(Modifier.fillMaxWidth().height(200.dp).background(Color.White)) {
                TopContrastUnderlay()
                Text(
                    "Room name, 3 people",
                    style = Type.note,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                )
            }
        }.assertAllTextFits()
    }

    @Test
    fun `the new messages marker fits in every language at a large text size`() {
        for (language in listOf("en", "ar", "de", "es", "fr", "pl", "ru", "zh")) {
            DesignHarness.render("chat-new-messages", widthDp = 272, heightDp = 80, fontScale = 1.3f, overVideo = true, language = language) {
                Box(Modifier.fillMaxWidth().height(80.dp)) {
                    NewMessagesMarker(Modifier.align(Alignment.Center)) {}
                }
            }.assertAllTextFits()
        }
    }
}
