package app.room

import app.utils.RoomRotation
import kotlin.test.Test
import kotlin.test.assertEquals

/** The two views of the room and how a stored value reads (#249). */
class RoomOrientationTest {
    @Test
    fun eachViewHoldsTheScreenItsOwnWay() {
        assertEquals(listOf(RoomOrientation.Landscape, RoomOrientation.Portrait), RoomOrientation.entries)
        assertEquals(RoomRotation.Landscape, RoomOrientation.Landscape.rotation)
        assertEquals(RoomRotation.Portrait, RoomOrientation.Portrait.rotation)
    }

    @Test
    fun aStoredViewReadsAsItselfAndAnyOtherValueAsLandscape() {
        for (view in RoomOrientation.entries) assertEquals(view, RoomOrientation.of(view.name))
        assertEquals(RoomOrientation.Landscape, RoomOrientation.of("Auto"), "The old Auto choice opens the room in landscape")
        assertEquals(RoomOrientation.Landscape, RoomOrientation.of(""))
        assertEquals(RoomOrientation.Landscape, RoomOrientation.of("Sideways"))
    }
}
