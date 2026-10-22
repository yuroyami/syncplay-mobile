package app.home

import app.protocol.OFFICIAL_SERVER_ADDRESS
import app.protocol.OFFICIAL_SERVER_NAME
import app.protocol.Session
import kotlin.io.encoding.Base64

/**
 * An invite link: the join details of a room as one line that someone can send.
 *
 * `synkplay://join?server=host&port=8997&room=Name&password=pw`
 *
 * The link carries the server password, because the link cannot join without it and the sender
 * would have to send it anyway. It never carries the operator password, which grants control and
 * not entry. It never carries a username either, since each person picks their own.
 *
 * No other scheme exists to match: the Syncplay desktop client has no invite link. Android and iOS
 * register the `synkplay` scheme.
 */
object InviteLink {

    const val SCHEME = "synkplay"
    private const val PREFIX = "$SCHEME://join"
    const val SHARE_PAGE = "https://yuroyami.github.io/syncplay-mobile/join/"

    /**
     * The invite link inside an HTTPS link to the share page. Messengers make HTTPS clickable, and
     * the fragment (after `#`) keeps the room credentials out of web requests.
     */
    fun shareUrl(config: JoinConfig): String = SHARE_PAGE + "#" + Base64.UrlSafe.encode(build(config).encodeToByteArray())

    /** A link names the official server by its host name, never by the address the client dials. */
    private const val OFFICIAL_IP = OFFICIAL_SERVER_ADDRESS
    private const val OFFICIAL_HOST = OFFICIAL_SERVER_NAME

    /** Length limits that match the join form, so a hostile link cannot pass a huge value. */
    private const val MAX_NAME = 149
    private const val MAX_ROOM = Session.MAX_ROOM_NAME_CHARS
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
     * Parses an invite link, or returns null when [raw] is not one. [raw] can be the `synkplay://`
     * link or the HTTPS share link. A link without a room gives null. Any other missing field
     * falls back to the default of a fresh join, so an incomplete link still gives usable details.
     */
    fun parse(raw: String): JoinConfig? {
        val trimmed = raw.trim()
        if (trimmed.length > 16_384) return null
        if (trimmed.startsWith("$SHARE_PAGE#", ignoreCase = true)) {
            val decoded = runCatching {
                Base64.UrlSafe.decode(trimmed.substringAfter('#')).decodeToString(throwOnInvalidSequence = true)
            }.getOrNull() ?: return null
            return if (decoded.startsWith("$PREFIX?", ignoreCase = true)) parse(decoded) else null
        }
        if (!trimmed.startsWith("$PREFIX?", ignoreCase = true)) return null
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
     * Trims and caps join details that come from outside the app (an invite link, a launcher
     * shortcut). Returns null when there is no room to join, the one field with no good default.
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
     * Splits the string that the app prints when it creates a managed room,
     * `+name:HASH12:PASSWORD`, into the room and the operator password (in upper case). Without
     * the split, pasting the whole string would create a room literally called
     * "+name:HASH12:PASSWORD".
     *
     * Returns the trimmed room and a blank password when there is nothing to split.
     */
    fun splitOperatorRoom(room: String): Pair<String, String> {
        val trimmed = room.trim()
        if (!trimmed.startsWith("+")) return trimmed to ""
        val match = OPERATOR_ROOM.find(trimmed) ?: return trimmed to ""
        return match.groupValues[1] to match.groupValues[2].uppercase()
    }

    /** `+base:HASH12` followed by `:XX-###-###`, the two shapes RoomPasswordProvider defines. */
    private val OPERATOR_ROOM = Regex("""^(\+.+:[A-Za-z0-9]{12}):([A-Za-z]{2}-\d{3}-\d{3})$""")

    /** Percent-encodes [value], keeping only ASCII letters, digits and `-_.~` as they are. */
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
                // A query encoder may write a space as '+', so '+' decodes to a space.
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
