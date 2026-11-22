package app.room

import app.utils.RoomRotation

/**
 * The two views of the room (#249). The full-screen key turns the room to the other view and stores
 * it in [app.preferences.Preferences.ROOM_ORIENTATION] as the name of the view, so each join opens
 * in the view that was stored last. The device never picks the view.
 */
enum class RoomOrientation {
    /** The room in landscape. This is the default. */
    Landscape,

    /** The room in portrait. */
    Portrait;

    /** What the room asks of the screen. */
    val rotation: RoomRotation
        get() = when (this) {
            Landscape -> RoomRotation.Landscape
            Portrait -> RoomRotation.Portrait
        }

    companion object {
        /** The view for a stored value. Any other value, such as the old "Auto", gives landscape. */
        fun of(stored: String): RoomOrientation = entries.firstOrNull { it.name == stored } ?: Landscape
    }
}
