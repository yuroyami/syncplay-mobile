package app.room.sharedplaylist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistOrderTest {

    private val list = listOf("a", "b", "c", "d", "e")

    @Test
    fun `moving down shifts the entries between up by one`() {
        assertEquals(listOf("a", "c", "d", "b", "e"), list.moved(1, 3))
    }

    @Test
    fun `moving up shifts the entries between down by one`() {
        assertEquals(listOf("d", "a", "b", "c", "e"), list.moved(3, 0))
    }

    @Test
    fun `every index follows its entry through a move`() {
        for (from in list.indices) for (to in list.indices) {
            val after = list.moved(from, to)
            for (i in list.indices) assertEquals(list[i], after[i.afterMove(from, to)], "entry $i, move $from to $to")
        }
    }

    @Test
    fun `no selection stays no selection`() {
        assertEquals(-1, (-1).afterMove(0, 3))
    }

    @Test
    fun `only an empty list set by nobody reads as a room that did not survive`() {
        assertTrue(cameBackEmpty(before = list, after = emptyList(), setBy = ""))
        assertFalse(cameBackEmpty(before = list, after = emptyList(), setBy = "alice"), "a person cleared it")
        assertFalse(cameBackEmpty(before = list, after = listOf("x"), setBy = ""), "the room still had a list")
        assertFalse(cameBackEmpty(before = emptyList(), after = emptyList(), setBy = ""), "there was nothing to lose")
    }
}
