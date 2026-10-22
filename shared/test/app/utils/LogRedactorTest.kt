package app.utils

import app.utils.LogRedactor.Kind
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LogRedactorTest {

    // Other tests register names through the protocol code, so each test starts from none.
    @BeforeTest
    @AfterTest
    fun reset() = LogRedactor.clearForTesting()

    @Test
    fun registeredValuesBecomeStablePlaceholders() {
        LogRedactor.register(Kind.User, "Christopher_Lee")
        LogRedactor.register(Kind.File, "The.Movie.2019.1080p.mkv")
        LogRedactor.register(Kind.Room, "movie night")
        val line = LogRedactor.redact("Christopher_Lee loaded: The.Movie.2019.1080p.mkv in movie night")
        assertEquals("<user-1> loaded: <file-1> in <room-1>", line)
        assertEquals("<user-1> left the room.", LogRedactor.redact("Christopher_Lee left the room."), "the same value keeps its placeholder")
    }

    @Test
    fun aNameInsideALongerWordStays() {
        LogRedactor.register(Kind.User, "bob")
        assertEquals("bobcat and <user-1>", LogRedactor.redact("bobcat and bob"))
    }

    @Test
    fun aShortValueIsNotRegistered() {
        LogRedactor.register(Kind.User, "al")
        assertEquals("al was here", LogRedactor.redact("al was here"))
    }

    @Test
    fun addressesAreReplacedByPattern() {
        val line = LogRedactor.redact("Server: Client connected from /192.168.1.5:52341 and /[fe80::1%en0]:5000, listening on 0.0.0.0")
        assertEquals("Server: Client connected from /<ip-1>:52341 and /<ip-2>:5000, listening on 0.0.0.0", line)
        assertEquals("again <ip-1>", LogRedactor.redact("again 192.168.1.5"), "the same address keeps its placeholder")
    }

    @Test
    fun aFullJvmIpv6AddressIsReplacedAndLoopbackStays() {
        val line = LogRedactor.redact("from /[fe80:0:0:0:1c2b:3dff:fe4e:5f60%wlan0]:40112 and /[0:0:0:0:0:0:0:1]:40113 at [12:34:56]")
        assertEquals("from /<ip-1>:40112 and /[0:0:0:0:0:0:0:1]:40113 at [12:34:56]", line)
    }

    @Test
    fun theValuesInAUrlQueryAreHidden() {
        val line = LogRedactor.redact("REQUEST: https://api.klipy.com/api/v1/***/gifs/search?q=funny%20cats&customer_id=1727-8812&page=2")
        assertEquals("REQUEST: https://api.klipy.com/api/v1/***/gifs/search?q=…&customer_id=…&page=…", line)
    }

    @Test
    fun aTimeIsNotMistakenForAnAddress() {
        val line = LogRedactor.redact("2026-09-26 04:15:00 | Sync: seeked to 1:23:45")
        assertFalse("<ip" in line, line)
    }
}
