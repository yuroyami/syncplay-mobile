package app.server.model

import app.utils.SyncClock

/**
 * Represents a room on the server.
 * Port of Python's Room class (syncplay-pc-src-master/syncplay/server.py).
 *
 * Tracks playback state (position, paused), playlist, and connected watchers.
 */
open class ServerRoom(val name: String) {

    companion object {
        const val STATE_PAUSED = 0
        const val STATE_PLAYING = 1
    }

    protected val _watchers = mutableMapOf<String, ServerWatcher>()
    protected var _playState: Int = STATE_PAUSED
    protected var _setBy: ServerWatcher? = null
    protected var _playlist: List<String> = emptyList()
    protected var _playlistIndex: Int? = null
    protected var _lastUpdate: Double = currentTimeSeconds()
    protected var _position: Double = 0.0

    /**
     * Whose position the room adopts when its own reading goes stale. Base rooms follow the
     * slowest watcher; a controlled room follows the slowest controller.
     */
    protected open fun positionCandidates(): Collection<ServerWatcher> = _watchers.values

    /**
     * Current playback position, advanced by wall-clock while playing.
     *
     * **This call mutates the room.** When the reading is more than a second stale it adopts
     * the slowest candidate's position and rewrites `_position`, `_setBy` and `_lastUpdate`.
     * That is faithful to Python's `Room.getPosition()`, and it means two calls in a row do
     * not return the same thing and the second one sees a `setBy` the first one chose. Read it
     * once and reuse the value; use [peekPosition] when you only want to look.
     */
    fun getPosition(): Double {
        val age = currentTimeSeconds() - _lastUpdate
        if (age > 1) {
            val slowest = positionCandidates().minOrNull()
            if (slowest != null) {
                _setBy = slowest
                _position = slowest.getPosition() ?: _position
                _lastUpdate = currentTimeSeconds()
                return _position
            }
        }
        return _position + (if (_playState == STATE_PLAYING) age else 0.0)
    }

    /** The same reading without adopting anyone. Safe to call as often as you like. */
    fun peekPosition(): Double =
        _position + (if (_playState == STATE_PLAYING) currentTimeSeconds() - _lastUpdate else 0.0)

    open fun setPaused(state: Int, setBy: ServerWatcher? = null) {
        _playState = state
        _setBy = setBy
    }

    open fun setPosition(position: Double, setBy: ServerWatcher? = null) {
        _position = position
        for (watcher in _watchers.values) {
            watcher.setPosition(position)
        }
        _setBy = setBy
    }

    fun isPlaying(): Boolean = _playState == STATE_PLAYING
    fun isPaused(): Boolean = _playState == STATE_PAUSED

    fun getSetBy(): ServerWatcher? = _setBy

    fun getWatchers(): List<ServerWatcher> = _watchers.values.toList()

    fun addWatcher(watcher: ServerWatcher) {
        if (_watchers.isNotEmpty()) {
            watcher.setPosition(getPosition())
        }
        _watchers[watcher.name] = watcher
        watcher.room = this
    }

    open fun removeWatcher(watcher: ServerWatcher) {
        if (watcher.name !in _watchers) return
        _watchers.remove(watcher.name)
        watcher.room = null
        if (_watchers.isEmpty()) {
            _position = 0.0
        }
    }

    fun isEmpty(): Boolean = _watchers.isEmpty()

    open fun setPlaylist(files: List<String>, setBy: ServerWatcher? = null) {
        _playlist = files
    }

    open fun setPlaylistIndex(index: Int, setBy: ServerWatcher? = null) {
        _playlistIndex = index
    }

    fun getPlaylist(): List<String> = _playlist
    fun getPlaylistIndex(): Int? = _playlistIndex

    open fun canControl(watcher: ServerWatcher): Boolean = true

    open fun getControllers(): List<ServerWatcher> = emptyList()

    override fun toString(): String = name

    protected fun currentTimeSeconds(): Double = SyncClock.nowSeconds()
}
