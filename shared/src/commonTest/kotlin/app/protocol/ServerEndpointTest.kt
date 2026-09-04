package app.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one place the dialled address and the verified certificate name are allowed to differ.
 * Getting this wrong is either a connection to nowhere or a TLS check against the wrong name,
 * and both have happened.
 */
class ServerEndpointTest {

    @Test
    fun the_official_name_dials_its_address_but_verifies_its_name() {
        val e = resolveServerEndpoint("syncplay.pl")
        assertEquals(OFFICIAL_SERVER_ADDRESS, e.dialHost)
        assertEquals("syncplay.pl", e.certificateHost, "the certificate is issued to the name")
    }

    @Test
    fun a_blank_host_is_the_official_server_not_a_connection_to_nowhere() {
        for (blank in listOf("", "   ", "\t")) {
            val e = resolveServerEndpoint(blank)
            assertEquals(OFFICIAL_SERVER_ADDRESS, e.dialHost, "blank input must not dial the empty string")
            assertEquals(OFFICIAL_SERVER_NAME, e.certificateHost)
        }
    }

    @Test
    fun any_other_server_dials_and_verifies_the_same_name() {
        val e = resolveServerEndpoint("syncplay.example.org")
        assertEquals("syncplay.example.org", e.dialHost)
        assertEquals("syncplay.example.org", e.certificateHost)
    }

    @Test
    fun surrounding_whitespace_never_reaches_either_field() {
        val e = resolveServerEndpoint("  my.server.tld  ")
        assertEquals("my.server.tld", e.dialHost)
        assertEquals("my.server.tld", e.certificateHost)
    }

    @Test
    fun a_bare_address_verifies_against_itself_and_is_never_swapped_for_the_official_name() {
        val e = resolveServerEndpoint("10.0.0.5")
        assertEquals("10.0.0.5", e.dialHost)
        assertEquals("10.0.0.5", e.certificateHost, "a typed address must not borrow syncplay.pl's identity")
    }

    @Test
    fun the_official_address_typed_by_hand_stays_an_address() {
        // Typing the IP is not the same as typing the name: no certificate is issued to it.
        val e = resolveServerEndpoint(OFFICIAL_SERVER_ADDRESS)
        assertEquals(OFFICIAL_SERVER_ADDRESS, e.dialHost)
        assertEquals(OFFICIAL_SERVER_ADDRESS, e.certificateHost)
    }
}
