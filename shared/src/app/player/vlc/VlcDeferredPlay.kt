package app.player.vlc

/**
 * Replays a play that libVLC ignored because a pause was still waiting to happen.
 *
 * Since 4.0.0a20, libVLC delays a pause that arrives while its input buffers, until the buffering
 * ends. This covers ":start-paused" on new media and a pause right after a seek. Meanwhile the
 * input reports Playing, so a play in that window changes nothing, and the late pause wins. The
 * engine arms this on every play, and plays again on the next Paused, unless a newer command came
 * in between. Every deliberate pause is a newer command, so this never undoes one.
 */
internal class VlcDeferredPlay {
    private var armedRevision: Long? = null

    /** A play was sent at command [revision]. */
    fun arm(revision: Long) {
        armedRevision = revision
    }

    /**
     * True when this Paused must be answered with a play. It is true at most once per [arm], and
     * never after a newer command, so a stale arm cannot outlive its command.
     */
    fun takeOnPaused(currentRevision: Long): Boolean {
        val revision = armedRevision ?: return false
        armedRevision = null
        return revision == currentRevision
    }
}
