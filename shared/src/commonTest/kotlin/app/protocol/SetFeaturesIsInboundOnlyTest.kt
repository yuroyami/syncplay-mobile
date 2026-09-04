package app.protocol

import app.protocol.wire.FileData
import app.protocol.wire.UserSetData
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `Set{"features": ...}` is a remote crash, not a feature.
 *
 * The reference server's `handleSet` sends the `features` command to
 * `Watcher.setFeatures`, which `server.py` never defines. Any client that sends one
 * takes down its own connection with an AttributeError inside the server's reactor.
 *
 * We decode it (our own server accepts one) but must never produce one. This test
 * fails the build if a builder starts emitting the key.
 */
class SetFeaturesIsInboundOnlyTest {

    private fun setPayload(message: WireMessage): JsonObject =
        syncplayJson.parseToJsonElement(message.toJson()).jsonObject
            .getValue("Set").jsonObject

    private val everyOutboundSet: List<Pair<String, WireMessage>> = listOf(
        "roomChange" to WireMessage.roomChange("room"),
        "file" to WireMessage.file(FileData(name = "a.mkv", duration = 1.0, size = "1")),
        "readiness" to WireMessage.readiness(isReady = true, manuallyInitiated = true),
        "playlistChange" to WireMessage.playlistChange(listOf("a.mkv")),
        "playlistIndex" to WireMessage.playlistIndex(0),
        "controllerAuth" to WireMessage.controllerAuth(room = "r", password = "AB-123-456"),
        "newControlledRoom" to WireMessage.newControlledRoom("+r:HASH", "AB-123-456"),
        "userBroadcast" to WireMessage.userBroadcast(mapOf("alice" to UserSetData()))
    )

    @Test
    fun no_builder_emits_a_top_level_features_key() {
        for ((name, message) in everyOutboundSet) {
            assertFalse(
                "features" in setPayload(message),
                "WireMessage.$name emitted Set{\"features\"}, which crashes the reference server"
            )
        }
    }

    @Test
    fun a_features_set_still_decodes_because_our_own_server_accepts_one() {
        val inbound = """{"Set":{"features":{"chat":true,"readiness":true}}}"""
        val decoded = syncplayJson.decodeFromString(WireMessageDeserializer, inbound)
        assertTrue(decoded is WireMessage.Set)
        assertTrue(decoded.data.features?.supportsChat == true)
    }
}
