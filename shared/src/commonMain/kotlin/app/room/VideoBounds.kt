package app.room

import kotlin.concurrent.Volatile

/**
 * Where the video is drawn inside the window, in pixels.
 *
 * Android's picture-in-picture animation starts from a rectangle that the app names. Without one,
 * the small window appears with no transition, and on return it covers the whole screen. The
 * video layer reports its position here on each layout, and the Android side reads it to build
 * the picture-in-picture parameters. [known] is false until the first measure.
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

    /**
     * Clears the rectangle when the room closes, so a later picture-in-picture never uses it. A
     * room is the group of people watching together.
     */
    fun forget() = report(0, 0, 0, 0)
}
