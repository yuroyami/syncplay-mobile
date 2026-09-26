package app.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import app.i18n.Localization
import app.theme.Space
import app.uicomponents.frames.Notice
import app.uicomponents.frames.NoticeSeverity
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The room's rewritten notices, from the real English strings, as they show over the video. Every
 * one fits in two lines at the default text size, and shows in full at double text size.
 */
class NoticeCopyGolden {

    private val notices: List<String>
        get() {
            val s = Localization.strings
            return listOf(
                s.roomConnectionFailed,
                s.roomAttemptingReconnection,
                s.roomSetAsReady,
                s.roomGuyJoined("Christopher_Lee", "movie night"),
                s.roomGuyLeft("Christopher_Lee"),
                s.roomRewinded("Christopher_Lee"),
                s.roomSlowdownNotification("Christopher_Lee"),
                s.roomSlowdownReverted,
                s.roomFileMismatchWarningCore("name, size, duration"),
                s.roomFileMismatchWarningRoom("name, size, duration"),
                s.roomSharedPlaylistChanged("Christopher_Lee"),
                s.roomSharedPlaylistNotFound("The.Movie.2019.1080p.mkv"),
                s.roomSharedPlaylistNoDirectories,
                s.roomSharedPlaylistLimit(250, 10000),
                s.roomPlaybackError("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"),
                s.roomSelectedSubError,
                s.roomSubErrorLoadVidFirst,
                s.roomMsgResolvingUrl,
                s.roomMsgResolveFailed,
                s.roomChatTooLong(512, 500),
                s.roomTrackChangeRefused,
                s.roomTlsRequiredDowngrade,
                s.mediaDropUnsupported,
            )
        }

    @Test
    fun rewrittenNoticesShowInFull() {
        for (scale in listOf(1f, 2f)) {
            val result = DesignHarness.render("notice-copy", 360, heightDp = 8000, fontScale = scale, overVideo = true) {
                Column(Modifier.padding(Space.gutter)) {
                    notices.forEach { Notice(it, NoticeSeverity.Warn) }
                }
            }
            assertTrue(result.contentHeightDp < 8000, "The canvas holds every notice: ${result.contentHeightDp}dp")
            result.assertAllTextFits()
            val shown = result.textLayouts.map { it.layoutInput.text.text }
            assertTrue(notices.all { it in shown }, "Every notice is drawn")
            if (scale == 1f) {
                // Two lines at most at the default text size, on a phone-wide room.
                val long = result.textLayouts.filter { it.lineCount > 2 }.map { "${it.lineCount} lines: ${it.layoutInput.text.text}" }
                assertTrue(long.isEmpty(), "Notices over two lines:\n" + long.joinToString("\n"))
            }
        }
    }
}
