package app.server.model

/**
 * A password-protected room where only authenticated controllers can change playback state.
 * Port of Python's ControlledRoom class (syncplay-pc-src-master/syncplay/server.py).
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
     * Disclosure path: deliberately returns an EMPTY list so newly joining watchers are
     * NOT told who the existing controllers are. Mirrors PC's
     * `ControlledRoom.getControllers()` (server.py), which returns `{}`.
     * Permission checks must use [canControl] / [getControllerWatchersInternal], never this.
     */
    override fun getControllers(): List<ServerWatcher> = emptyList()

    /** Internal accessor for server-side logic only (never sent to clients). */
    fun getControllerWatchersInternal(): List<ServerWatcher> = _controllers.values.toList()

    override fun removeWatcher(watcher: ServerWatcher) {
        super.removeWatcher(watcher)
        _controllers.remove(watcher.name)
    }

}
