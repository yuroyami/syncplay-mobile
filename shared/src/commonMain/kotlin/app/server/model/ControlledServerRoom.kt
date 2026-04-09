package app.server.model

import app.utils.generateTimestampMillis

/**
 * A password-protected room where only authenticated controllers can change playback state.
 * Port of Python's ControlledRoom class (syncplay-pc-src-master/syncplay/server.py).
 *
 * Room name format: `+roomBaseName:HASH12CHARS`
 */
class ControlledServerRoom(name: String) : ServerRoom(name) {

    private val _controllers = mutableMapOf<String, ServerWatcher>()

    /**
     * Uses the slowest controller's position instead of the slowest watcher's.
     */
    override fun getPosition(): Double {
        val age = currentTimeSeconds() - _lastUpdate
        if (_controllers.isNotEmpty() && age > 1) {
            val watcher = _controllers.values.minOrNull()
            if (watcher != null) {
                _setBy = watcher
                _position = watcher.getPosition() ?: _position
                _lastUpdate = currentTimeSeconds()
                return _position
            }
        }
        return _position + (if (_playState == STATE_PLAYING) age else 0.0)
    }

    fun addController(watcher: ServerWatcher) {
        _controllers[watcher.name] = watcher
    }

    override fun setPaused(state: Int, setBy: ServerWatcher?) {
        if (setBy != null && canControl(setBy)) {
            super.setPaused(state, setBy)
        }
    }

    override fun setPosition(position: Double, setBy: ServerWatcher?) {
        if (setBy != null && canControl(setBy)) {
            super.setPosition(position, setBy)
        }
    }

    override fun setPlaylist(files: List<String>, setBy: ServerWatcher?) {
        if (setBy != null && canControl(setBy)) {
            super.setPlaylist(files, setBy)
        }
    }

    override fun setPlaylistIndex(index: Int, setBy: ServerWatcher?) {
        if (setBy != null && canControl(setBy)) {
            super.setPlaylistIndex(index, setBy)
        }
    }

    override fun canControl(watcher: ServerWatcher): Boolean {
        return watcher.name in _controllers
    }

    override fun getControllers(): List<ServerWatcher> = _controllers.values.toList()

    override fun removeWatcher(watcher: ServerWatcher) {
        super.removeWatcher(watcher)
        _controllers.remove(watcher.name)
    }

    private fun currentTimeSeconds(): Double = generateTimestampMillis() / 1000.0
}
