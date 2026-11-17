package app.protocol.network

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The failure texts that reach the shared code from the iOS network manager. SwiftNIO runs on
 * Network.framework there, and the manager passes the Swift description of each error on as the
 * message. The texts were measured on macOS with NWConnection, the same framework.
 */
class NetworkFrameworkFailureTest {

    @Test
    fun anUnknownNameIsTheResolver() {
        assertEquals(ConnectionFailure.NameNotFound, classifyDialFailure(Exception("-65554: NoSuchRecord")))
    }

    @Test
    fun aClosedPortIsARefusal() {
        assertEquals(ConnectionFailure.Refused, classifyDialFailure(Exception("POSIXErrorCode(rawValue: 61): Connection refused")))
        // A closed port on the same machine answers with a reset instead.
        assertEquals(ConnectionFailure.Refused, classifyDialFailure(Exception("POSIXErrorCode(rawValue: 54): Connection reset by peer")))
    }

    @Test
    fun theConnectTimerIsATimeout() {
        assertEquals(ConnectionFailure.TimedOut, classifyDialFailure(Exception("Connect timeout (10 seconds)")))
    }
}
