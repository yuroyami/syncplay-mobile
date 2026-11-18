package app.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import app.i18n.EnAppStrings
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A tap on the GIF drawer that misses its keys, for example just beside the source tabs, must not
 * reach the room behind the drawer. There it hides the controls, and the drawer with them.
 */
class GifPanelTapTest {
    /** Scene pixels per dp. The harness draws at a density of 2. */
    private val density = 2f

    private fun RoomRig.Room.trendingTab(): SemanticsNode = assertNotNull(
        allNodes().firstOrNull { nameOf(it) == EnAppStrings.roomGifTabTrending },
        "The Trending tab shows",
    )

    private fun RoomRig.Room.assertTapKeepsControls(at: Offset, where: String) {
        driver.tap(at)
        driver.frames(10)
        assertTrue(ui.visibleHUD.value, "A tap $where leaves the controls up")
        assertTrue(ui.gifPanelVisible.value, "A tap $where leaves the GIF drawer open")
    }

    @Test
    fun aMissedTapOnTheLandscapeDrawerKeepsTheControls() =
        RoomRig.drive(solo = false, television = false, withVideo = true) {
            ui.toggleGifPanel(true)
            driver.frames(20)
            val tab = boundsOf(trendingTab())
            assertTapKeepsControls(Offset(tab.center.x, tab.top - 2f * density), "just above the source tabs")
            assertTapKeepsControls(Offset(tab.center.x, tab.bottom + 3f * density), "just below the source tabs")
            // The drawer's side margin, between the drawer and the edge of the chat column.
            val margin = assertNotNull(
                allNodes().filter { nameOf(it) == EnAppStrings.roomGifTabGifs || nameOf(it) == EnAppStrings.roomGifTabStickers }
                    .minByOrNull { boundsOf(it).left },
                "The type switch shows",
            )
            assertTapKeepsControls(Offset(boundsOf(margin).left - 6f * density, tab.center.y), "on the drawer's side margin")
            // The test has no network, so the grid area under the tabs holds no tile.
            assertTapKeepsControls(Offset(tab.center.x, tab.bottom + 120f * density), "on the empty grid")
        }

    @Test
    fun aMissedTapOnThePortraitDrawerKeepsTheControls() =
        RoomRig.drive(solo = false, television = false, withVideo = true, widthDp = 390, heightDp = 844) {
            ui.toggleGifPanel(true)
            driver.frames(20)
            val tab = boundsOf(trendingTab())
            assertTapKeepsControls(Offset(tab.center.x, tab.top - 2f * density), "just above the source tabs")
        }
}
