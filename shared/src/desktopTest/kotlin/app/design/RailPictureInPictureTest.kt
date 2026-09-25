package app.design

import app.i18n.EnAppStrings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Many televisions have no picture-in-picture. On such a device, the system refuses the window,
 * so the rail must not offer it.
 */
class RailPictureInPictureTest {

    @Test
    fun theRailOffersPictureInPictureOnlyWhereThePlatformHasIt() {
        for (supported in listOf(true, false)) {
            RoomRig.drive(television = false, withVideo = true, pictureInPicture = supported) {
                ui.railActionsExpanded.value = true
                driver.frames(30)
                val offered = described(EnAppStrings.roomOverflowPip) != null
                assertEquals(supported, offered, "Picture-in-picture offered when the platform support is $supported")
            }
        }
    }
}
