package app.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JoinPortTest {

    @Test
    fun `a port from 1 to 65535 is accepted, spaces trimmed`() {
        assertEquals(1, parsePort("1"))
        assertEquals(8999, parsePort(" 8999 "))
        assertEquals(65535, parsePort("65535"))
    }

    @Test
    fun `anything that is not a TCP port is refused`() {
        listOf("", " ", "0", "-1", "65536", "99999", "89a9", "8999.0").forEach { assertNull(parsePort(it), "'$it'") }
    }
}
