package app.protocol.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The official server's address fallback fires on exactly one kind of failure. Getting this wrong
 * is not a crash, it is a doubled wait: every unreachable-host attempt would dial a second address
 * for another full connect timeout before reporting what it already knew.
 */
class DialFailureTest {

    @Test
    fun a_connect_timeout_is_not_the_resolver() {
        assertFalse(isNameResolutionFailure(Exception("Timed out waiting for 10000 ms")))
    }

    @Test
    fun a_refused_connection_is_not_the_resolver() {
        assertFalse(isNameResolutionFailure(Exception("Connection refused: no further information")))
    }

    @Test
    fun the_platform_wordings_are_all_recognised() {
        val wordings = listOf(
            "nodename nor servname provided, or not known",
            "Name or service not known",
            "Unable to resolve host \"syncplay.pl\": No address associated with hostname",
            "failed to resolve 'syncplay.pl' after 2 queries",
        )
        for (text in wordings) {
            assertTrue(isNameResolutionFailure(Exception(text)), "not recognised: $text")
        }
    }

    @Test
    fun a_wrapped_resolver_failure_is_found_through_the_cause_chain() {
        val root = Exception("Name or service not known")
        val wrapped = Exception("Connection attempt failed", Exception("dialling", root))
        assertTrue(isNameResolutionFailure(wrapped))
    }

    @Test
    fun a_cause_cycle_does_not_hang_the_walk() {
        // Deliberately self-referential: the depth cap is the only thing stopping this.
        val looping = object : Exception("looping") {
            override val cause: Throwable get() = this
        }
        assertFalse(isNameResolutionFailure(looping))
    }

    @Test
    fun nothing_at_all_is_not_the_resolver() {
        assertFalse(isNameResolutionFailure(Exception()))
    }
}
