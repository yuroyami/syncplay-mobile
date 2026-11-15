package app.room

import app.utils.RoomRotation
import kotlin.test.Test
import kotlin.test.assertEquals

/** The rules of the "Room orientation" setting and of the full-screen key (#249). */
class RoomOrientationTest {
    @Test
    fun eachChoiceGivesItsOwnViewUntilTheKeySwitchesIt() {
        assertEquals(RoomRotation.Landscape, RoomOrientation.Landscape.rotation(switched = false))
        assertEquals(RoomRotation.Portrait, RoomOrientation.Landscape.rotation(switched = true))
        assertEquals(RoomRotation.Portrait, RoomOrientation.Portrait.rotation(switched = false))
        assertEquals(RoomRotation.Landscape, RoomOrientation.Portrait.rotation(switched = true))
        assertEquals(RoomRotation.FollowDevice, RoomOrientation.Auto.rotation(switched = false))
        assertEquals(RoomRotation.Landscape, RoomOrientation.Auto.rotation(switched = true), "The key holds landscape")
    }

    @Test
    fun aRoomThatDoesNotTurnOffersFullScreenInATallWindowAndItsExitInAWideOne() {
        for (choice in listOf(RoomOrientation.Landscape, RoomOrientation.Portrait)) for (switched in listOf(false, true)) {
            assertEquals(RoomViewKey.FullScreen, choice.key(switched, tall = true))
            assertEquals(RoomViewKey.ExitFullScreen, choice.key(switched, tall = false))
        }
    }

    @Test
    fun aRoomThatFollowsTheDeviceOffersTheLock() {
        assertEquals(RoomViewKey.FullScreen, RoomOrientation.Auto.key(switched = false, tall = true))
        assertEquals(RoomViewKey.LockRotation, RoomOrientation.Auto.key(switched = false, tall = false))
        for (tall in listOf(false, true)) {
            assertEquals(RoomViewKey.UnlockRotation, RoomOrientation.Auto.key(switched = true, tall = tall))
        }
    }

    @Test
    fun aStoredValueGivesItsChoiceAndAnUnknownOneGivesLandscape() {
        for (choice in RoomOrientation.entries) assertEquals(choice, RoomOrientation.of(choice.name))
        assertEquals(RoomOrientation.Landscape, RoomOrientation.of(""))
        assertEquals(RoomOrientation.Landscape, RoomOrientation.of("Sideways"))
    }
}
