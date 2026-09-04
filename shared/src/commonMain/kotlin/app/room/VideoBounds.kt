package app.room

/**
 * Where the video is drawn inside the window, in pixels.
 *
 * Android's picture-in-picture animation morphs out of a rectangle the app names, and without one
 * the window appears out of nowhere and lands back over the whole screen. The room's video layer
 * reports its own position here as it is laid out, and the Android side reads it when it builds
 * the picture-in-picture parameters. Null until the video layer has been measured once.
 */
object VideoBounds {

    @Volatile
    var left: Int = 0
        private set

    @Volatile
    var top: Int = 0
        private set

    @Volatile
    var right: Int = 0
        private set

    @Volatile
    var bottom: Int = 0
        private set

    /** True once the video layer has reported a rectangle with real size. */
    val known: Boolean get() = right > left && bottom > top

    fun report(left: Int, top: Int, right: Int, bottom: Int) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    /** Forgotten when the room closes, so a later picture-in-picture never uses a stale rectangle. */
    fun forget() = report(0, 0, 0, 0)
}
