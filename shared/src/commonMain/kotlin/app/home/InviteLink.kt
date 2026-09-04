package app.home

import app.protocol.OFFICIAL_SERVER_ADDRESS
import app.protocol.OFFICIAL_SERVER_NAME

/**
 * The room as one line someone can send.
 *
 * `synkplay://join?server=host&port=8997&room=Name&password=pw`
 *
 * The server password rides along because without it the link cannot join, and the alternative is
 * the sender dictating it anyway. The operator password never does: it grants control, not entry.
 * A username is never carried either, since each person picks their own.
 *
 * There is no scheme to interoperate with. The desktop client has no invite link, so this one is
 * ours, and both platforms register it.
 */
object InviteLink {

    const val SCHEME = "synkplay"
    private const val PREFIX = "$SCHEME://join"

    /** The official server is named, not resolved to the address the client happens to dial. */
    private const val OFFICIAL_IP = OFFICIAL_SERVER_ADDRESS
    private const val OFFICIAL_HOST = OFFICIAL_SERVER_NAME

    /** Caps matching the join form, so a hostile link cannot hand the room a megabyte of name. */
    private const val MAX_NAME = 149
    private const val MAX_ROOM = 34
    private const val MAX_HOST = 255

    fun build(config: JoinConfig): String {
        val host = if (config.ip == OFFICIAL_IP) OFFICIAL_HOST else config.ip
        val parts = buildList {
            add("server=" + encode(host))
            add("port=${config.port}")
            add("room=" + encode(config.room))
            if (config.pw.isNotEmpty()) add("password=" + encode(config.pw))
        }
        return PREFIX + "?" + parts.joinToString("&")
    }

    /**
     * Reads a link back, or returns null when [raw] is not one. Anything missing falls back to the
     * defaults a fresh join would use, so a half-written link still lands somewhere sensible.
     */
    fun parse(raw: String): JoinConfig? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("$SCHEME://", ignoreCase = true)) return null
        val query = trimmed.substringAfter('?', "")
        if (query.isEmpty()) return null

        val fields = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            if (name.isNotEmpty()) fields[name.lowercase()] = decode(value)
        }

        val room = fields["room"]?.trim().orEmpty()
        if (room.isEmpty()) return null
        val default = JoinConfig()
        return JoinConfig(
            user = default.user,
            room = room.take(MAX_ROOM),
            ip = fields["server"]?.trim()?.take(MAX_HOST)?.ifEmpty { null } ?: default.ip,
            port = fields["port"]?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 } ?: default.port,
            pw = fields["password"].orEmpty().take(MAX_NAME),
        )
    }

    /**
     * Caps and trims a join built from outside the app (an invite link, a launcher shortcut).
     * Returns null when there is no room to join, which is the one field with no sensible default.
     */
    fun sanitize(config: JoinConfig): JoinConfig? {
        val room = config.room.trim()
        if (room.isEmpty()) return null
        val default = JoinConfig()
        return JoinConfig(
            user = config.user.trim().take(MAX_NAME).ifEmpty { default.user },
            room = room.take(MAX_ROOM),
            ip = config.ip.trim().take(MAX_HOST).ifEmpty { default.ip },
            port = config.port.takeIf { it in 1..65535 } ?: default.port,
            pw = config.pw.take(MAX_NAME),
        )
    }

    /**
     * The string the app itself prints when a managed room is created, `+name:HASH12:PASSWORD`,
     * split into the room and the operator password. Pasting it whole used to create a room
     * literally called "+name:HASH12:PASSWORD".
     *
     * Returns the room unchanged and a blank password when there is nothing to split.
     */
    fun splitOperatorRoom(room: String): Pair<String, String> {
        val trimmed = room.trim()
        if (!trimmed.startsWith("+")) return trimmed to ""
        val match = OPERATOR_ROOM.find(trimmed) ?: return trimmed to ""
        return match.groupValues[1] to match.groupValues[2].uppercase()
    }

    /** `+base:HASH12` followed by `:XX-###-###`, the two shapes RoomPasswordProvider defines. */
    private val OPERATOR_ROOM = Regex("""^(\+.+:[A-Za-z0-9]{12}):([A-Za-z]{2}-\d{3}-\d{3})$""")

    /** Percent-encoding, restricted to what a query value may hold. */
    private fun encode(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-_.~") append(c)
            else append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF])
        }
    }

    private fun decode(value: String): String {
        if ('%' !in value && '+' !in value) return value
        val bytes = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '%' && i + 2 < value.length -> {
                    val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex == null) {
                        bytes.add(c.code.toByte()); i++
                    } else {
                        bytes.add(hex.toByte()); i += 3
                    }
                }
                // A query encoder may write a space as '+', so read it back as one.
                c == '+' -> { bytes.add(' '.code.toByte()); i++ }
                else -> {
                    for (b in c.toString().encodeToByteArray()) bytes.add(b)
                    i++
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private const val HEX = "0123456789ABCDEF"
}
