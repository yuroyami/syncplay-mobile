package app.home

import app.preferences.Preferences
import app.preferences.set
import app.preferences.value
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One recent join for the list on Home: the name, the room, the server and its port. It keeps no
 * password. When the server had one, [hasPassword] is true, and the join asks for it again.
 */
@Serializable
data class RecentJoin(
    val user: String,
    val room: String,
    val ip: String,
    val port: Int,
    val hasPassword: Boolean = false,
) {
    /** The server as the list shows it. */
    val server: String get() = "$ip:$port"

    /** The same room on the same server, whatever name joined it. */
    fun sameRoomAs(other: RecentJoin): Boolean =
        room == other.room && port == other.port && ip.equals(other.ip, ignoreCase = true)

    /** The join details for this entry, with no password. */
    fun toJoinConfig(): JoinConfig = JoinConfig(user = user, room = room, ip = ip, port = port)
}

/**
 * The recent joins, newest first, at most [MAX]. [JoinConfig.save] adds each join while the
 * "Remember joining info" setting is on.
 */
object RecentJoins {
    const val MAX = 5

    /** The saved list. A value that does not decode reads as an empty list. */
    fun read(): List<RecentJoin> = decode(Preferences.RECENT_JOINS.value())

    fun decode(json: String?): List<RecentJoin> =
        runCatching { json?.let { Json.decodeFromString<List<RecentJoin>>(it) } }.getOrNull().orEmpty()

    /**
     * [list] with [config] first. The same room on the same server counts once. A room on this
     * device's own server stays out, because it exists only while the device hosts it.
     */
    internal fun withJoin(list: List<RecentJoin>, config: JoinConfig): List<RecentJoin> {
        if (config.ip == LOCAL_HOST) return list
        val entry = RecentJoin(config.user, config.room, config.ip, config.port, hasPassword = config.pw.isNotEmpty())
        return (listOf(entry) + list.filterNot { it.sameRoomAs(entry) }).take(MAX)
    }

    suspend fun add(config: JoinConfig) = write(withJoin(read(), config))

    suspend fun remove(entry: RecentJoin) = write(read().filterNot { it.sameRoomAs(entry) })

    private suspend fun write(list: List<RecentJoin>) = Preferences.RECENT_JOINS.set(Json.encodeToString(list))
}
