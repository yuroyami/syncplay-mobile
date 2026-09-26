package app.protocol.replay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Replaces every user, room and file name that [isOurs] does not accept with a stand-in, so a
 * session recorded on a public server keeps nobody else's names. The same name always gets the
 * same stand-in. A line with nothing to replace is kept exactly as it arrived.
 *
 * It knows where the protocol puts names: the rooms and users of a `List` answer, the users of a
 * `Set`, readiness, the shared playlist, operator rooms, the `State` that says who set it, chat and
 * `Hello`. Free text (chat messages, the message of the day, errors) is kept.
 */
class Redactor(private val isOurs: (String) -> Boolean) {

    private val standIns = mutableMapOf<String, String>()
    private val counts = mutableMapOf<String, Int>()

    @Synchronized
    fun redact(line: String): String {
        val root = runCatching { Json.parseToJsonElement(line) }.getOrNull() as? JsonObject ?: return line
        val clean = top(root)
        return if (clean == root) line else Json.encodeToString(JsonElement.serializer(), clean)
    }

    /** A name's stand-in: `user-1`, `room-1`, `file-1.mkv`. A file keeps its extension. */
    @Synchronized
    fun name(kind: String, name: String): String {
        if (isOurs(name)) return name
        return standIns.getOrPut("$kind:$name") {
            val n = counts.merge(kind, 1, Int::plus)!!
            val extension = if (kind == "file") name.substringAfterLast('.', "").take(5).let { if (it.isEmpty()) "" else ".$it" } else ""
            "$kind-$n$extension"
        }
    }

    private fun top(o: JsonObject) = o.edit { key, value ->
        when (key) {
            "List" -> (value as? JsonObject)?.let(::list) ?: value
            "Set" -> (value as? JsonObject)?.let(::set) ?: value
            "State" -> (value as? JsonObject)?.let(::state) ?: value
            "Chat" -> (value as? JsonObject)?.let { it.field("username", "user") } ?: value
            "Hello" -> (value as? JsonObject)?.let(::hello) ?: value
            else -> value
        }
    }

    private fun list(rooms: JsonObject) = JsonObject(rooms.entries.associate { (room, users) ->
        name("room", room) to ((users as? JsonObject)?.let { u ->
            JsonObject(u.entries.associate { (user, data) -> name("user", user) to ((data as? JsonObject)?.let(::userData) ?: data) })
        } ?: users)
    })

    private fun set(o: JsonObject) = o.edit { key, value ->
        val obj = value as? JsonObject ?: return@edit value
        when (key) {
            "user" -> JsonObject(obj.entries.associate { (user, data) -> name("user", user) to ((data as? JsonObject)?.let(::userData) ?: data) })
            "ready" -> obj.field("username", "user")
            "playlistChange" -> obj.field("user", "user").edit { k, v -> if (k == "files" && v is JsonArray) files(v) else v }
            "playlistIndex" -> obj.field("user", "user")
            "controllerAuth" -> obj.field("user", "user").field("room", "room")
            "newControlledRoom" -> obj.field("roomName", "room")
            "room" -> obj.field("name", "room")
            else -> value
        }
    }

    private fun state(o: JsonObject) = o.edit { key, value ->
        if (key == "playstate" && value is JsonObject) value.field("setBy", "user") else value
    }

    private fun hello(o: JsonObject) = o.field("username", "user").edit { key, value ->
        if (key == "room" && value is JsonObject) value.field("name", "room") else value
    }

    private fun userData(o: JsonObject) = o.edit { key, value ->
        when {
            key == "file" && value is JsonObject -> value.field("name", "file")
            key == "room" && value is JsonObject -> value.field("name", "room")
            else -> value
        }
    }

    private fun files(a: JsonArray) = JsonArray(a.map { f -> (f as? JsonPrimitive)?.takeIf { it.isString }?.let { JsonPrimitive(name("file", it.content)) } ?: f })

    private fun JsonObject.field(key: String, kind: String): JsonObject = edit { k, v ->
        if (k == key && v is JsonPrimitive && v.isString) JsonPrimitive(name(kind, v.content)) else v
    }

    private inline fun JsonObject.edit(change: (String, JsonElement) -> JsonElement) =
        JsonObject(entries.associate { (k, v) -> k to change(k, v) })
}
