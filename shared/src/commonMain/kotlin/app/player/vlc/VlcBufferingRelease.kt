package app.player.vlc

/**
 * Clears the waiting indicator of the iOS VLC engine once the picture moves again.
 *
 * The bundled VLCKit reports every native buffering event as Buffering, the one at 100 percent
 * too, and drops the percentage. So its state can stay Buffering while playback runs, and nothing
 * else clears the flag. Two clock samples in a row that move forward, while the flag is set, prove
 * that the buffering ended. The flag is for display only, so nothing in the sync path changes.
 */
internal class VlcBufferingRelease {
    private var lastMs: Long? = null
    private var advances = 0

    /**
     * Takes one clock sample. True when the flag should clear: [buffering] is set and the clock
     * moved forward on the last two samples. A clock that stands still or goes back starts over.
     */
    fun onSample(buffering: Boolean, clockMs: Long): Boolean {
        if (!buffering || clockMs < 0L) {
            lastMs = null
            advances = 0
            return false
        }
        val previous = lastMs
        lastMs = clockMs
        if (previous != null) advances = if (clockMs > previous) advances + 1 else 0
        return advances >= 2
    }
}
