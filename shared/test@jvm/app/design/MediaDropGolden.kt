package app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.i18n.strings
import app.uicomponents.DroppedItem
import app.uicomponents.DroppedMedia
import app.uicomponents.MediaDropOverlay
import app.uicomponents.MediaDropTarget
import kotlin.test.Test
import kotlin.test.assertEquals

/** While an item is over the window, the overlay says what a drop will do, and the sentence fits. */
class MediaDropGolden {

    private val longName = "A long film name, the director's cut, remastered (2019).mkv"

    @Test
    fun theRoomSaysWhatADropWillDo() {
        val cases = listOf(
            listOf(DroppedItem.File("/films/$longName", isDirectory = false)) to "Drop to play $longName",
            listOf(DroppedItem.Text("https://example.com/v.mp4")) to "Drop to play this link",
            listOf(DroppedItem.File("/notes/todo.txt", isDirectory = false)) to "This file type cannot be played.",
            listOf(DroppedItem.File("/films", isDirectory = true)) to "A folder cannot be played. Drop one video file.",
            null to "Drop a video file or a link",
        )
        cases.forEachIndexed { index, (items, expected) ->
            for (scale in listOf(1f, 2f)) {
                val target = MediaDropTarget { }.apply { started(items); entered() }
                val result = DesignHarness.render("drop-room-$index", 480, heightDp = 360, fontScale = scale, overVideo = true) {
                    Box(Modifier.fillMaxWidth().height(360.dp)) {
                        MediaDropOverlay(target) { media ->
                            when (media) {
                                is DroppedMedia.File -> strings.roomDropPlayFile(media.name)
                                is DroppedMedia.Link -> strings.roomDropPlayLink
                            }
                        }
                    }
                }
                assertEquals(listOf(expected), result.textLayouts.map { it.layoutInput.text.text })
                result.assertAllTextFits()
            }
        }
    }

    @Test
    fun theHomeScreenSaysTheDropWatchesAlone() {
        val target = MediaDropTarget { }.apply { started(listOf(DroppedItem.File("/films/clip.mp4", isDirectory = false))); entered() }
        val result = DesignHarness.render("drop-home", 360, heightDp = 360) {
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                MediaDropOverlay(target) { media ->
                    when (media) {
                        is DroppedMedia.File -> strings.homeDropWatchAloneFile(media.name)
                        is DroppedMedia.Link -> strings.homeDropWatchAloneLink
                    }
                }
            }
        }
        assertEquals(listOf("Drop to watch clip.mp4 alone"), result.textLayouts.map { it.layoutInput.text.text })
        result.assertAllTextFits()
    }

    /** Nothing shows while no item is over the window. */
    @Test
    fun nothingShowsWithoutADrag() {
        val target = MediaDropTarget { }
        val result = DesignHarness.render("drop-none", 360, heightDp = 200) {
            Box(Modifier.fillMaxWidth().height(200.dp)) { MediaDropOverlay(target) { "" } }
        }
        assertEquals(emptyList(), result.textLayouts.map { it.layoutInput.text.text })
    }
}
