package app.design

import app.klipy.KlipyMediaType
import app.room.ui.chat.GifDrawerHeader
import app.room.ui.chat.GifSource
import kotlin.test.Test

/**
 * The GIF drawer's header at the widths that the chat column gives it. The chat column is 36
 * percent of the room: about 313dp inside the drawer on a 914dp phone, 272dp on an 800dp one,
 * and 420dp or more on a tablet or desktop. The three source labels must keep every word at each.
 */
class GifSourcesGolden {

    @Test
    fun sourceLabelsKeepTheirWordsAtEveryChatWidth() {
        for (w in listOf(272, 313, 420, 600)) {
            val result = DesignHarness.render("gif-header", w, heightDp = 120) {
                GifDrawerHeader(
                    type = KlipyMediaType.GIF,
                    onType = {},
                    source = GifSource.TRENDING,
                    onSource = {},
                )
            }
            result.assertAllTextFits()
        }
        DesignHarness.render("gif-header", 313, heightDp = 120, fontScale = 1.3f) {
            GifDrawerHeader(type = KlipyMediaType.STICKER, onType = {}, source = GifSource.FAVORITES, onSource = {})
        }.assertAllTextFits()
    }
}
