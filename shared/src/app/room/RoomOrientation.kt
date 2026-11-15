package app.room

import app.preferences.Preferences
import app.preferences.value
import app.utils.RoomRotation

/**
 * The choices of the "Room orientation" setting (#249). The stored value is the name of the
 * choice. The full-screen key can switch the room to the other view for one room.
 */
enum class RoomOrientation {
    /** The room stays in landscape. This is the default. */
    Landscape,

    /** The room stays in portrait. */
    Portrait,

    /** The room follows the device. */
    Auto;

    /** What the room asks of the screen. [switched] is true while the key has switched the view. */
    fun rotation(switched: Boolean): RoomRotation = when (this) {
        Landscape -> if (switched) RoomRotation.Portrait else RoomRotation.Landscape
        Portrait -> if (switched) RoomRotation.Landscape else RoomRotation.Portrait
        Auto -> if (switched) RoomRotation.Landscape else RoomRotation.FollowDevice
    }

    /**
     * What the full-screen key offers. [tall] is true in a window that is taller than wide. The
     * name follows the window, so it stays true where the system ignores the room's request.
     */
    fun key(switched: Boolean, tall: Boolean): RoomViewKey = when {
        this == Auto && switched -> RoomViewKey.UnlockRotation
        tall -> RoomViewKey.FullScreen
        this == Auto -> RoomViewKey.LockRotation
        else -> RoomViewKey.ExitFullScreen
    }

    companion object {
        /** The choice for a stored value. An unknown value gives the default. */
        fun of(stored: String): RoomOrientation = entries.firstOrNull { it.name == stored } ?: Landscape

        /** The choice that the setting holds now. */
        fun current(): RoomOrientation = of(Preferences.ROOM_ORIENTATION.value())
    }
}

/** What a tap on the full-screen key does next. */
enum class RoomViewKey {
    /** Turns the portrait room to landscape. */
    FullScreen,

    /** Turns a landscape room that does not follow the device to portrait. */
    ExitFullScreen,

    /** Holds a room that follows the device in landscape. */
    LockRotation,

    /** Ends that hold, so the room follows the device again. */
    UnlockRotation,
}
