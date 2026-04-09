package app.server.model

import app.utils.generateTimestampMillis

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
     * Returns the current playback position, adjusted for elapsed time if playing.
     * If watchers exist and the last update is stale (>1s), uses the slowest watcher's position.
     * Port of Python's Room.getPosition().
     */
    open fun getPosition(): Double {
        val age = currentTimeSeconds() - _lastUpdate
        if (_watchers.isNotEmpty() && age > 1) {
            val watcher = _watchers.values.minOrNull()
            if (watcher != null) {
                _setBy = watcher
                _position = watcher.getPosition() ?: _position
                _lastUpdate = currentTimeSeconds()
                return _position
            }
        }
        return _position + (if (_playState == STATE_PLAYING) age else 0.0)
    }

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

    private fun currentTimeSeconds(): Double = generateTimestampMillis() / 1000.0
}
