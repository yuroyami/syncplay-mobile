package app.protocol.network

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [decideTls] runs before the socket opens. A decision after the server answers is too late,
 * because the Hello with the password hash has already gone out in plain text.
 */
class TlsPolicyTest {

    @Test
    fun `required always asks when the transport can`() {
        assertEquals(TlsDecision.ASK, decideTls(enabled = false, required = true, transportSupportsTls = true))
        assertEquals(TlsDecision.ASK, decideTls(enabled = true, required = true, transportSupportsTls = true))
    }

    @Test
    fun `required refuses a transport with no TLS`() {
        assertEquals(TlsDecision.REFUSE, decideTls(enabled = true, required = true, transportSupportsTls = false))
        assertEquals(TlsDecision.REFUSE, decideTls(enabled = false, required = true, transportSupportsTls = false))
    }

    @Test
    fun `optional asks only when enabled and possible`() {
        assertEquals(TlsDecision.ASK, decideTls(enabled = true, required = false, transportSupportsTls = true))
        assertEquals(TlsDecision.PLAIN, decideTls(enabled = true, required = false, transportSupportsTls = false))
        assertEquals(TlsDecision.PLAIN, decideTls(enabled = false, required = false, transportSupportsTls = true))
        assertEquals(TlsDecision.PLAIN, decideTls(enabled = false, required = false, transportSupportsTls = false))
    }
}
