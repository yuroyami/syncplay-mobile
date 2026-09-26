package app.protocol.replay

import app.protocol.WireMessageDeserializer
import app.protocol.sync.FASTFORWARD_THRESHOLD
import app.protocol.sync.REWIND_THRESHOLD
import app.protocol.sync.SLOWDOWN_THRESHOLD
import app.protocol.sync.decideSync
import app.protocol.syncplayJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Replays recorded sessions with no network: one on the public server syncplay.pl and one on the
 * app's own server, each seen from both clients. RecordSessionsTest records them.
 *
 * A changed decode rule shows as a line that decodes differently from when it was recorded. A
 * changed threshold or sync rule shows as a decision that comes out differently.
 */
class RecordedSessionReplayTest {

    private val fixtures: List<File> =
        File("src/desktopTest/resources/replay").listFiles { f -> f.extension == "jsonl" }.orEmpty().sortedBy { it.name }

    private fun records(file: File): List<SessionRecord> = file.readLines().filter { it.isNotBlank() }.map(SessionCodec::decode)

    @Test
    fun bothServersAreRecordedFromBothSides() {
        assertEquals(listOf("builtin-alice", "builtin-bob", "public-alice", "public-bob"), fixtures.map { it.nameWithoutExtension })
    }

    @Test
    fun everyRecordedLineDecodesAsItDidWhenRecorded() {
        for (file in fixtures) for (record in records(file)) when (record) {
            is SessionRecord.LineIn -> assertEquals(record.decoded, SessionCodec.decodedForm(record.line), "${file.name}: ${record.line.take(160)}")
            // The server decodes the client's lines with the same decoder.
            is SessionRecord.LineOut -> SessionCodec.decodedForm(record.line)
            is SessionRecord.Decision -> Unit
        }
    }

    /** The thresholds are today's constants, so a changed threshold changes the replay. */
    @Test
    fun everyRecordedDecisionComesOutTheSame() {
        for (file in fixtures) {
            records(file).filterIsInstance<SessionRecord.Decision>().forEachIndexed { i, decision ->
                val ctx = decision.ctx.copy(
                    rewindThreshold = REWIND_THRESHOLD,
                    slowdownThreshold = SLOWDOWN_THRESHOLD,
                    fastForwardThreshold = FASTFORWARD_THRESHOLD,
                )
                assertEquals(decision.outcome, decideSync(decision.playstate, decision.before, ctx), "${file.name}, decision $i")
            }
        }
    }

    /** The recordings hold every kind of message and every correction, or the replay proves little. */
    @Test
    fun theRecordingsCoverTheProtocol() {
        for (file in fixtures) {
            val records = records(file)
            val decoded = records.filterIsInstance<SessionRecord.LineIn>().joinToString("\n") { it.decoded }
            val kinds = listOf(
                "Hello(", "ListResponse(", "State(", "user={", "ChatBroadcast(",
                "playlistChange=PlaylistChangeData(", "playlistIndex=PlaylistIndexData(", "ready=ReadyData(",
            )
            kinds.forEach { kind -> assertTrue(kind in decoded, "${file.name} has no $kind") }
            if (file.name.startsWith("public")) assertTrue("TLS(" in decoded, "${file.name} has no TLS answer")
            if (file.name.endsWith("alice.jsonl")) {
                assertTrue("newControlledRoom=NewControlledRoom(" in decoded, "${file.name} has no managed room")
                assertTrue("controllerAuth=ControllerAuthData(" in decoded, "${file.name} has no operator answer")
            }
        }
        val actions = fixtures.flatMap { records(it) }.filterIsInstance<SessionRecord.Decision>().flatMap { it.outcome.actions }
        for (kind in listOf("FirstSync", "SomeoneSeeked", "SomeoneBehind", "SlowDown", "RestoreSpeed", "SomeonePaused", "SomeonePlayed", "Nudge")) {
            assertTrue(actions.any { it::class.simpleName == kind }, "No recorded decision has $kind")
        }
    }

    /**
     * A peer can send any shape. Each recorded line is changed (a field dropped, a value of another
     * type, the line cut short), and the decoder must either read the result or throw a
     * [SerializationException]. The message handler catches only that one, so anything else would
     * end the session (issue #152 was one such case).
     */
    @Test
    fun aChangedLineOnlyEverFailsAsASerializationError() {
        val escaped = mutableListOf<String>()
        var tried = 0
        val lines = fixtures.flatMap { records(it) }.mapNotNull {
            when (it) {
                is SessionRecord.LineIn -> it.line
                is SessionRecord.LineOut -> it.line
                is SessionRecord.Decision -> null
            }
        }.distinct()
        for (line in lines) for (mutated in mutations(line)) {
            tried++
            try {
                syncplayJson.decodeFromString(WireMessageDeserializer, mutated)
            } catch (_: SerializationException) {
                // The handler skips the line and the session goes on.
            } catch (other: Throwable) {
                escaped += "${other::class.simpleName}: ${other.message?.take(80)} <- ${mutated.take(160)}"
            }
        }
        assertTrue(tried > 1_000, "Enough changed lines: $tried")
        assertTrue(escaped.isEmpty(), "${escaped.size} of $tried changed lines escaped as another error:\n" + escaped.distinct().take(20).joinToString("\n"))
    }

    private fun mutations(line: String): List<String> {
        val cuts = listOf(1, line.length / 4, line.length / 2, line.length * 3 / 4, line.length - 1).filter { it in 1 until line.length }.map { line.take(it) }
        val root = runCatching { Json.parseToJsonElement(line) }.getOrNull() as? JsonObject ?: return cuts
        val swaps = listOf(JsonNull, JsonPrimitive("x"), JsonPrimitive(7), JsonPrimitive(-1.5), JsonPrimitive(true), JsonArray(emptyList()), JsonObject(emptyMap()))
        val out = mutableListOf<String>()
        fun walk(element: JsonElement, rebuild: (JsonElement?) -> JsonElement, depth: Int) {
            if (depth > 5) return
            when (element) {
                is JsonObject -> for ((key, value) in element) {
                    val inner: (JsonElement?) -> JsonElement = { v ->
                        rebuild(JsonObject(if (v == null) element - key else element.toMutableMap().apply { put(key, v) }))
                    }
                    out += inner(null).toString()
                    swaps.filter { it != value }.forEach { out += inner(it).toString() }
                    walk(value, inner, depth + 1)
                }
                is JsonArray -> element.forEachIndexed { i, value ->
                    val inner: (JsonElement?) -> JsonElement = { v ->
                        rebuild(JsonArray(element.toMutableList().apply { if (v == null) removeAt(i) else set(i, v) }))
                    }
                    swaps.filter { it != value }.forEach { out += inner(it).toString() }
                    walk(value, inner, depth + 1)
                }
                else -> Unit
            }
        }
        walk(root, { it ?: JsonObject(emptyMap()) }, 0)
        return (cuts + out).distinct()
    }
}
