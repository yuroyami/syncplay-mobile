package app.protocol.sync

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The offset is how far a viewer's copy of a file runs ahead of the room. The two conversions share
 * one sign convention. A sign error shows up only for a viewer who set an offset, so it is easy to
 * miss.
 */
class OffsetTest {

    @Test
    fun `a local seek is announced in room time`() {
        // The local copy runs 10 s ahead, so the local 40 s is the room's 30 s.
        assertEquals(30.0, localToRoomSeconds(localMs = 40_000L, offsetSeconds = 10.0))
        assertEquals(50.0, localToRoomSeconds(localMs = 40_000L, offsetSeconds = -10.0))
    }

    @Test
    fun `a room position becomes a local target`() {
        assertEquals(40_000.0, roomToLocalMs(roomMs = 30_000.0, offsetSeconds = 10.0))
        assertEquals(20_000.0, roomToLocalMs(roomMs = 30_000.0, offsetSeconds = -10.0))
    }

    @Test
    fun `no offset changes nothing`() {
        assertEquals(40.0, localToRoomSeconds(localMs = 40_000L, offsetSeconds = 0.0))
        assertEquals(40_000.0, roomToLocalMs(roomMs = 40_000.0, offsetSeconds = 0.0))
    }

    @Test
    fun `the two conversions undo each other`() {
        val offset = 7.5
        val local = 123_456L
        assertEquals(local.toDouble(), roomToLocalMs(localToRoomSeconds(local, offset) * 1000.0, offset))
    }
}
