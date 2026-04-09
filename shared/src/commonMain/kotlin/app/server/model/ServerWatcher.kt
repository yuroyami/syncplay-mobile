package app.server.model

import app.server.SyncplayServer
import app.server.model.ServerConfig.Companion.MAX_FILENAME_LENGTH
import app.utils.generateTimestampMillis
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents a connected client on the server side.
 * Port of the Python Watcher class (syncplay-pc-src-master/syncplay/server.py).
 *
 * Tracks per-client state: position, pause, file info, readiness.
 */
class ServerWatcher(
    val server: SyncplayServer,
    private val _name: String
) : Comparable<ServerWatcher> {

    var room: ServerRoom? = null
        internal set

    private var _position: Double? = null
    private var _lastUpdatedOn: Double = currentTimeSeconds()

    var file: JsonObject? = null
        private set

    var ready: Boolean? = null
    var features: JsonObject? = null
    var version: String? = null

    val name: String get() = _name

    fun getPosition(): Double? {
        val pos = _position ?: return null
        val elapsed = if (room?.isPlaying() == true) {
            currentTimeSeconds() - _lastUpdatedOn
        } else 0.0
        return pos + elapsed
    }

    fun setPosition(position: Double) {
        _position = position
    }

    fun setFile(fileData: JsonObject?) {
        file = fileData?.let { truncateFileName(it) }
        server.sendFileUpdate(this)
    }

    private fun truncateFileName(fileJson: JsonObject): JsonObject {
        val name = fileJson["name"]?.jsonPrimitive?.content ?: return fileJson
        if (name.length <= MAX_FILENAME_LENGTH) return fileJson
        val builder = kotlinx.serialization.json.buildJsonObject {
            for ((k, v) in fileJson) {
                if (k == "name") {
                    put(k, kotlinx.serialization.json.JsonPrimitive(name.take(MAX_FILENAME_LENGTH)))
                } else {
                    put(k, v)
                }
            }
        }
        return builder
    }

    /**
     * Processes an incoming state update from this client.
     * Port of Python's Watcher.updateState().
     */
    fun updateState(position: Double?, paused: Boolean?, doSeek: Boolean?, messageAge: Double) {
        val pauseChanged = hasPauseChanged(paused)
        _lastUpdatedOn = currentTimeSeconds()

        if (pauseChanged && paused != null) {
            room?.setPaused(
                if (paused) ServerRoom.STATE_PAUSED else ServerRoom.STATE_PLAYING,
                setBy = this
            )
        }

        if (position != null) {
            val adjusted = if (paused == false) position + messageAge else position
            setPosition(adjusted)
        }

        if (doSeek == true || pauseChanged) {
            server.forcePositionUpdate(this, doSeek == true, paused ?: true)
        }
    }

    private fun hasPauseChanged(paused: Boolean?): Boolean {
        if (paused == null) return false
        val roomPaused = room?.isPaused() ?: return false
        return (roomPaused && !paused) || (!roomPaused && paused)
    }

    fun isController(): Boolean {
        val r = room ?: return false
        return RoomPasswordProvider.isControlledRoom(r.name) && r.canControl(this)
    }

    fun isReady(): Boolean? {
        if (server.config.disableReady) return null
        return ready
    }

    override fun compareTo(other: ServerWatcher): Int {
        val myPos = getPosition()
        val otherPos = other.getPosition()
        if (myPos == null || file == null) return 1
        if (otherPos == null || other.file == null) return -1
        return myPos.compareTo(otherPos)
    }

    companion object {
        fun currentTimeSeconds(): Double = generateTimestampMillis() / 1000.0
    }
}
