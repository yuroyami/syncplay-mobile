package app.protocol

import app.protocol.network.formatIpAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IpAddressTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun ipv4IsDotted() {
        assertEquals("151.80.32.178", formatIpAddress(bytes(151, 80, 32, 178)))
        assertEquals("255.0.0.1", formatIpAddress(bytes(255, 0, 0, 1)))
    }

    @Test
    fun ipv6ShortensTheLongestZeroRun() {
        assertEquals("::1", formatIpAddress(bytes(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)))
        assertEquals(
            "2001:db8::1:0:1",
            formatIpAddress(bytes(0x20, 0x01, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1)),
        )
        // A single zero group is not shortened.
        assertEquals(
            "2001:db8:0:1:1:1:1:1",
            formatIpAddress(bytes(0x20, 0x01, 0x0d, 0xb8, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1)),
        )
    }

    @Test
    fun ipv4CarriedInIpv6IsPlainIpv4() {
        assertEquals("10.0.0.7", formatIpAddress(bytes(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff, 0xff, 10, 0, 0, 7)))
    }

    @Test
    fun anyOtherLengthIsNotAnAddress() {
        assertNull(formatIpAddress(bytes(1, 2, 3)))
    }
}
