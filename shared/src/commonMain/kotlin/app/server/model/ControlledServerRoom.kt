package app.server.model

/**
 * A password-protected room where only authenticated controllers can change the playback state.
 * A port of the ControlledRoom class in the Syncplay PC server (syncplay/server.py).
 *
 * Room name format: `+roomBaseName:HASH12CHARS`
 */
class ControlledServerRoom(name: String) : ServerRoom(name) {

    private val _controllers = mutableMapOf<String, ServerWatcher>()

    /** A controlled room follows the slowest controller, not the slowest watcher. */
    override fun positionCandidates(): Collection<ServerWatcher> = _controllers.values

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

    /**
     * Returns an EMPTY list on purpose, so watchers who join are NOT told who the controllers
     * are. This matches the PC server's `ControlledRoom.getControllers()` (server.py), which
     * returns `{}`. Permission checks must use [canControl] or [getControllerWatchersInternal],
     * never this.
     */
    override fun getControllers(): List<ServerWatcher> = emptyList()

    /** Internal accessor for server-side logic only (never sent to clients). */
    fun getControllerWatchersInternal(): List<ServerWatcher> = _controllers.values.toList()

    override fun removeWatcher(watcher: ServerWatcher) {
        super.removeWatcher(watcher)
        _controllers.remove(watcher.name)
    }

}
