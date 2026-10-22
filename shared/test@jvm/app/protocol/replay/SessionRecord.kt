package app.protocol.replay

import app.protocol.WireMessageDeserializer
import app.protocol.sync.SyncAction
import app.protocol.sync.SyncContext
import app.protocol.sync.SyncOutcome
import app.protocol.sync.SyncPrefs
import app.protocol.sync.SyncState
import app.protocol.syncplayJson
import app.protocol.wire.PlaystateData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * One event of a recorded session: a line from the server, a line to the server, or one sync
 * decision with everything it was decided from. A fixture file holds one record per line.
 */
sealed interface SessionRecord {
    /** A line from the server, and what the decoder made of it when it was recorded. */
    data class LineIn(val line: String, val decoded: String) : SessionRecord

    /** A line that the client sent. */
    data class LineOut(val line: String) : SessionRecord

    /** One call of the sync decision and its result. */
    data class Decision(val playstate: PlaystateData?, val before: SyncState, val ctx: SyncContext, val outcome: SyncOutcome) : SessionRecord
}

/** Reads and writes [SessionRecord]s as JSON lines. */
object SessionCodec {

    private val json = Json

    /** What the real decoder makes of [line], as the text the replay compares. */
    fun decodedForm(line: String): String = syncplayJson.decodeFromString(WireMessageDeserializer, line).toString()

    fun encode(record: SessionRecord): String = json.encodeToString(JsonObject.serializer(), when (record) {
        is SessionRecord.LineIn -> buildJsonObject { put("t", "in"); put("line", record.line); put("decoded", record.decoded) }
        is SessionRecord.LineOut -> buildJsonObject { put("t", "out"); put("line", record.line) }
        is SessionRecord.Decision -> buildJsonObject {
            put("t", "decision")
            put("playstate", record.playstate?.let { syncplayJson.encodeToJsonElement(PlaystateData.serializer(), it) } ?: JsonNull)
            put("before", state(record.before))
            put("ctx", ctx(record.ctx))
            put("after", state(record.outcome.state))
            put("actions", JsonArray(record.outcome.actions.map(::action)))
        }
    })

    fun decode(line: String): SessionRecord {
        val o = json.parseToJsonElement(line).jsonObject
        return when (val type = o.string("t")) {
            "in" -> SessionRecord.LineIn(o.string("line"), o.string("decoded"))
            "out" -> SessionRecord.LineOut(o.string("line"))
            "decision" -> SessionRecord.Decision(
                playstate = o["playstate"]?.takeIf { it !is JsonNull }?.let { syncplayJson.decodeFromJsonElement(PlaystateData.serializer(), it) },
                before = state(o["before"]!!.jsonObject),
                ctx = ctx(o["ctx"]!!.jsonObject),
                outcome = SyncOutcome(state(o["after"]!!.jsonObject), o["actions"]!!.jsonArray.map { action(it.jsonObject) }),
            )
            else -> error("Unknown record type $type")
        }
    }

    private fun state(s: SyncState) = buildJsonObject {
        put("serverIgnFly", s.serverIgnFly)
        put("clientIgnFly", s.clientIgnFly)
        put("globalPaused", s.globalPaused)
        put("globalPositionMs", s.globalPositionMs)
        put("lastGlobalPositionSetAt", s.lastGlobalPositionSetAt?.toString())
        put("lastGlobalUpdate", s.lastGlobalUpdate?.toString())
        put("behindFirstDetected", s.behindFirstDetected?.toString())
        put("speedChanged", s.speedChanged)
        put("nudgeLevel", s.nudgeLevel)
        put("smoothedDiff", s.smoothedDiff)
    }

    private fun state(o: JsonObject) = SyncState(
        serverIgnFly = o.int("serverIgnFly"),
        clientIgnFly = o.int("clientIgnFly"),
        globalPaused = o.bool("globalPaused"),
        globalPositionMs = o.double("globalPositionMs"),
        lastGlobalPositionSetAt = o.instant("lastGlobalPositionSetAt"),
        lastGlobalUpdate = o.instant("lastGlobalUpdate"),
        behindFirstDetected = o.instant("behindFirstDetected"),
        speedChanged = o.bool("speedChanged"),
        nudgeLevel = o.int("nudgeLevel"),
        smoothedDiff = o["smoothedDiff"]?.jsonPrimitive?.doubleOrNull,
    )

    private fun ctx(c: SyncContext) = buildJsonObject {
        put("now", c.now.toString())
        put("playerPositionMs", c.playerPositionMs)
        put("hasMedia", c.hasMedia)
        put("isInBackground", c.isInBackground)
        put("supportsSpeedAdjustment", c.supportsSpeedAdjustment)
        put("selfName", c.selfName)
        put("followerInControlledRoom", c.followerInControlledRoom)
        put("rewind", c.prefs.rewind)
        put("fastForward", c.prefs.fastForward)
        put("slowdown", c.prefs.slowdown)
        put("dontSlowWithMe", c.prefs.dontSlowWithMe)
        put("messageAge", c.messageAge)
        put("userOffsetSeconds", c.userOffsetSeconds)
        put("rewindThreshold", c.rewindThreshold)
        put("slowdownThreshold", c.slowdownThreshold)
        put("fastForwardThreshold", c.fastForwardThreshold)
        put("seekPending", c.seekPending)
    }

    private fun ctx(o: JsonObject) = SyncContext(
        now = Instant.parse(o.string("now")),
        playerPositionMs = o.double("playerPositionMs"),
        hasMedia = o.bool("hasMedia"),
        isInBackground = o.bool("isInBackground"),
        supportsSpeedAdjustment = o.bool("supportsSpeedAdjustment"),
        selfName = o.string("selfName"),
        followerInControlledRoom = o.bool("followerInControlledRoom"),
        prefs = SyncPrefs(o.bool("rewind"), o.bool("fastForward"), o.bool("slowdown"), o.bool("dontSlowWithMe")),
        messageAge = o.double("messageAge"),
        userOffsetSeconds = o.double("userOffsetSeconds"),
        rewindThreshold = o.double("rewindThreshold"),
        slowdownThreshold = o.double("slowdownThreshold"),
        fastForwardThreshold = o.double("fastForwardThreshold"),
        seekPending = o.bool("seekPending"),
    )

    private fun action(a: SyncAction) = buildJsonObject {
        put("type", a::class.simpleName)
        when (a) {
            is SyncAction.FirstSync -> { put("seekToMs", a.seekToMs); put("paused", a.paused) }
            is SyncAction.SomeoneSeeked -> { put("by", a.by); put("toSeconds", a.toSeconds) }
            is SyncAction.SomeoneBehind -> { put("by", a.by); put("toSeconds", a.toSeconds) }
            is SyncAction.SomeoneFastForwarded -> { put("by", a.by); put("toSeconds", a.toSeconds) }
            is SyncAction.SlowDown -> put("by", a.by)
            is SyncAction.SomeonePlayed -> put("by", a.by)
            is SyncAction.SomeonePaused -> put("by", a.by)
            is SyncAction.Nudge -> put("rate", a.rate)
            SyncAction.RestoreSpeed -> Unit
        }
    }

    private fun action(o: JsonObject): SyncAction = when (val type = o.string("type")) {
        "FirstSync" -> SyncAction.FirstSync(o["seekToMs"]!!.jsonPrimitive.long, o.bool("paused"))
        "SomeoneSeeked" -> SyncAction.SomeoneSeeked(o.string("by"), o.double("toSeconds"))
        "SomeoneBehind" -> SyncAction.SomeoneBehind(o.string("by"), o.double("toSeconds"))
        "SomeoneFastForwarded" -> SyncAction.SomeoneFastForwarded(o.string("by"), o.double("toSeconds"))
        "SlowDown" -> SyncAction.SlowDown(o.string("by"))
        "SomeonePlayed" -> SyncAction.SomeonePlayed(o.string("by"))
        "SomeonePaused" -> SyncAction.SomeonePaused(o.string("by"))
        "Nudge" -> SyncAction.Nudge(o.double("rate"))
        "RestoreSpeed" -> SyncAction.RestoreSpeed
        else -> error("Unknown action $type")
    }

    private fun JsonObject.string(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.bool(key: String) = this[key]!!.jsonPrimitive.boolean
    private fun JsonObject.int(key: String) = this[key]!!.jsonPrimitive.int
    private fun JsonObject.double(key: String) = this[key]!!.jsonPrimitive.double
    private fun JsonObject.instant(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(Instant::parse)
}
