package app.design

import app.room.RoomOrientation
import app.room.RoomUiStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The page rules of the room state. In one-page mode (the portrait room), the main box under the
 * video shows one page at a time in place of chat, and Back shows chat again. Outside the mode,
 * the landscape rules stay: a panel and the GIF drawer can be open together. The landscape hold of
 * the full-screen key ends with Back, after the open layers.
 */
class OnePageTest {

    private enum class Kind { Panel, ToolPanel, Gifs, Controls, More }

    /** A page of the main box: its name, how its tab or key opens it, and its flag. */
    private class Page(
        val name: String,
        val kind: Kind,
        val open: (RoomUiStateManager) -> Unit,
        val toggle: (RoomUiStateManager) -> Unit,
        val flag: (RoomUiStateManager) -> MutableStateFlow<Boolean>,
    )

    private val pages = listOf(
        Page("people", Kind.Panel, { it.toggleUserInfo(true) }, { it.toggleUserInfo() }, { it.tabCardUserInfo }),
        Page("playlist", Kind.Panel, { it.toggleSharedPlaylist(true) }, { it.toggleSharedPlaylist() }, { it.tabCardSharedPlaylist }),
        Page("preferences", Kind.Panel, { it.toggleRoomPreferences(true) }, { it.toggleRoomPreferences() }, { it.tabCardRoomPreferences }),
        Page("tracks", Kind.ToolPanel, { it.toggleTracks(true) }, { it.toggleTracks() }, { it.tabCardTracks }),
        Page("gestures", Kind.ToolPanel, { it.toggleGestures(true) }, { it.toggleGestures() }, { it.tabCardGestures }),
        Page("seek to", Kind.ToolPanel, { it.toggleSeekTo(true) }, { it.toggleSeekTo() }, { it.tabCardSeekTo }),
        Page("add media", Kind.ToolPanel, { it.toggleAddMedia(true) }, { it.toggleAddMedia() }, { it.tabCardAddMedia }),
        Page("GIF drawer", Kind.Gifs, { it.toggleGifPanel(true) }, { it.toggleGifPanel() }, { it.gifPanelVisible }),
        Page("Controls", Kind.Controls, { it.toggleControlPanel(true) }, { it.toggleControlPanel() }, { it.controlPanel }),
        Page("More", Kind.More, { it.toggleMorePage(true) }, { it.toggleMorePage() }, { it.railActionsExpanded }),
    )

    private fun RoomUiStateManager.openPages(): Set<String> = pages.filter { it.flag(this).value }.map { it.name }.toSet()

    private fun RoomUiStateManager.reset() {
        pages.forEach { it.flag(this).value = false }
        askLeave.value = false
        orientationSwitch.value = null
    }

    private fun model(onePage: Boolean, block: (RoomUiStateManager) -> Unit) = RoomRig.model { room ->
        room.uiState.onePage = onePage
        block(room.uiState)
    }

    @Test
    fun inOnePageModeAPageClosesEveryOtherPage() = model(onePage = true) { ui ->
        for (first in pages) for (second in pages) {
            if (first === second) continue
            ui.reset()
            first.open(ui)
            second.open(ui)
            assertEquals(setOf(second.name), ui.openPages(), "${first.name}, then ${second.name}")
        }
    }

    /**
     * Today's landscape rules: one side panel at a time, a tool panel closes the control strip,
     * the strip closes the tool panels, and the GIF drawer and the room actions stay with anything.
     */
    private fun landscapeAfter(first: Page, second: Page): Set<String> {
        val open = mutableSetOf(first.name)
        when (second.kind) {
            Kind.Panel, Kind.ToolPanel -> {
                open -= pages.filter { it.kind == Kind.Panel || it.kind == Kind.ToolPanel }.map { it.name }.toSet()
                if (second.kind == Kind.ToolPanel) open -= "Controls"
            }
            Kind.Controls -> open -= pages.filter { it.kind == Kind.ToolPanel }.map { it.name }.toSet()
            Kind.Gifs, Kind.More -> Unit
        }
        return open + second.name
    }

    @Test
    fun outsideOnePageModeThePagesKeepTheLandscapeRules() = model(onePage = false) { ui ->
        for (first in pages) for (second in pages) {
            if (first === second) continue
            ui.reset()
            first.open(ui)
            second.open(ui)
            assertEquals(landscapeAfter(first, second), ui.openPages(), "${first.name}, then ${second.name}")
        }
        // A panel and the GIF drawer open together, as they sit in different docks.
        ui.reset()
        ui.toggleGifPanel(true)
        ui.toggleRoomPreferences(true)
        assertEquals(setOf("GIF drawer", "preferences"), ui.openPages())
    }

    @Test
    fun inOnePageModeBackClosesThePageThenAsksToLeave() = model(onePage = true) { ui ->
        for (page in pages) {
            ui.reset()
            page.open(ui)
            ui.back()
            assertEquals(emptySet(), ui.openPages(), "Back closes the ${page.name} page")
            assertFalse(ui.askLeave.value, "Back closes the ${page.name} page and does not ask to leave")
            ui.back()
            assertTrue(ui.askLeave.value, "With no page open, Back asks to leave")
        }
    }

    @Test
    fun outsideOnePageModeBackClosesOneLayerAtATime() = model(onePage = false) { ui ->
        ui.reset()
        ui.toggleGifPanel(true)
        ui.toggleRoomPreferences(true)
        ui.toggleControlPanel(true)
        ui.toggleMorePage(true)
        assertEquals(setOf("GIF drawer", "preferences", "Controls", "More"), ui.openPages())
        val left = listOf(
            setOf("preferences", "Controls", "More"),
            setOf("Controls", "More"),
            setOf("More"),
            emptySet(),
        )
        for (expected in left) {
            ui.back()
            assertEquals(expected, ui.openPages())
            assertFalse(ui.askLeave.value)
        }
        ui.back()
        assertTrue(ui.askLeave.value, "With nothing open, Back asks to leave")
    }

    @Test
    fun theKeyOfTheOpenPageShowsChatAgain() = model(onePage = true) { ui ->
        for (page in pages) {
            ui.reset()
            page.toggle(ui)
            assertEquals(setOf(page.name), ui.openPages())
            page.toggle(ui)
            assertEquals(emptySet(), ui.openPages(), "A second tap on the key of the ${page.name} page shows chat")
        }
    }

    @Test
    fun aTapOnTheChatFieldKeepsOnlyTheGifDrawer() = model(onePage = true) { ui ->
        for (page in pages) {
            ui.reset()
            page.open(ui)
            ui.showChat(keepGifs = true)
            val expected = if (page.kind == Kind.Gifs) setOf(page.name) else emptySet()
            assertEquals(expected, ui.openPages(), "A tap on the chat field over the ${page.name} page")
        }
    }

    @Test
    fun enteringOnePageModeKeepsOnePage() = model(onePage = false) { ui ->
        ui.reset()
        ui.toggleGifPanel(true)
        ui.toggleRoomPreferences(true)
        ui.toggleControlPanel(true)
        ui.toggleMorePage(true)
        ui.onePage = true
        assertEquals(setOf("GIF drawer"), ui.openPages(), "The GIF drawer wins, because the composer holds its search")

        ui.onePage = false
        ui.reset()
        ui.toggleUserInfo(true)
        ui.expandMediaAdd()
        assertEquals(emptySet(), ui.openPages(), "The add card of the landscape bar closes the people panel")
        ui.onePage = true
        assertEquals(setOf("add media"), ui.openPages(), "The add card becomes the add media page")
        assertFalse(ui.mediaAddExpanded.value)
    }

    @Test
    fun theFullScreenKeySwitchesTheViewAndBack() = model(onePage = true) { ui ->
        ui.reset()
        ui.toggleOrientationSwitch()
        assertTrue(ui.orientationSwitched, "The full-screen key switches the view")
        ui.toggleOrientationSwitch()
        assertFalse(ui.orientationSwitched, "The same key switches it back")
    }

    @Test
    fun backClosesAPanelThenEndsTheSwitchThenAsksToLeave() {
        for (onePage in listOf(false, true)) model(onePage) { ui ->
            ui.reset()
            ui.toggleOrientationSwitch()
            ui.toggleRoomPreferences(true)
            ui.back()
            assertEquals(emptySet(), ui.openPages(), "Back closes the panel first (one-page mode: $onePage)")
            assertTrue(ui.orientationSwitched, "The switch stays while a panel was open (one-page mode: $onePage)")
            ui.back()
            assertFalse(ui.orientationSwitched, "Back then ends the switch (one-page mode: $onePage)")
            assertFalse(ui.askLeave.value, "Back that ends the switch does not ask to leave (one-page mode: $onePage)")
            ui.back()
            assertTrue(ui.askLeave.value, "Back then asks to leave (one-page mode: $onePage)")
        }
    }

    @Test
    fun theSwitchLastsForOneRoom() = model(onePage = false) { ui ->
        ui.reset()
        ui.toggleOrientationSwitch()
        ui.invalidate()
        assertFalse(ui.orientationSwitched, "A room that ends drops the switch")
    }

    @Test
    fun aSwitchMadeUnderAnotherChoiceOfTheSettingDoesNotCount() = model(onePage = false) { ui ->
        ui.reset()
        // The rig keeps the default choice, Landscape.
        ui.toggleOrientationSwitch(RoomOrientation.Auto)
        assertFalse(ui.orientationSwitched, "A change of the setting ends the switch")
        ui.back()
        assertTrue(ui.askLeave.value, "Back has no switch to end, so it asks to leave")
    }
}
