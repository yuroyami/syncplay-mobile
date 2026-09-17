package app.protocol.sync

/** Owns the advertised target until the queued player command has run or been discarded. */
class PendingSeekPosition(val targetMs: Long)

/** The protocol holds its sync lock around every access. Completion belongs to one command. */
class PendingSeekPositions {
    var current: PendingSeekPosition? = null
        private set

    fun begin(action: SyncAction, selfName: String): PendingSeekPosition? {
        val target = when (action) {
            is SyncAction.FirstSync -> action.seekToMs
            is SyncAction.SomeoneSeeked -> if (action.by != selfName) (action.toSeconds * 1000.0).toLong() else null
            is SyncAction.SomeoneBehind -> if (action.by != selfName) (action.toSeconds * 1000.0).toLong() else null
            is SyncAction.SomeoneFastForwarded -> if (action.by != selfName) (action.toSeconds * 1000.0).toLong() else null
            else -> null
        } ?: return null
        return PendingSeekPosition(target).also { current = it }
    }

    fun complete(command: PendingSeekPosition) {
        // An older Main task may finish after the network has already accepted another seek.
        if (current === command) current = null
    }

    fun clear() { current = null }
}
