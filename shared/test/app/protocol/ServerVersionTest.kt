package app.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerVersionTest {

    @Test
    fun comparesPartByPartAsNumbers() {
        assertEquals(true, versionAtLeast("1.7.5", "1.4.0"))
        assertEquals(true, versionAtLeast("1.4.0", "1.4.0"))
        assertEquals(true, versionAtLeast("1.10.0", "1.4.0"))
        assertEquals(false, versionAtLeast("1.3.4", "1.4.0"))
        assertEquals(false, versionAtLeast("0.27.0", "1.4.0"))
    }

    @Test
    fun aMissingPartCountsAsZeroAndASuffixIsIgnored() {
        assertEquals(true, versionAtLeast("1.4", "1.4.0"))
        assertEquals(true, versionAtLeast("1.7.5-beta", "1.4.0"))
        assertEquals(false, versionAtLeast("1.3.9rc1", "1.4.0"))
    }

    @Test
    fun aVersionWithNoNumberCannotBeCompared() {
        assertNull(versionAtLeast("unknown", "1.4.0"))
        assertNull(versionAtLeast("", "1.4.0"))
    }
}
