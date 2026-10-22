package app.protocol.models

import app.player.models.MediaFile

/** One user in the current room, as modelled from the server's `List`/`Set` payloads. */
data class User(
    var index: Int = 0, // roster order, assigned locally; 0 for the current user
    var name: String = "",
    var readiness: Boolean, // whether the user is ready (set by the user or by a controller)
    var file: MediaFile?, // file the user is playing, null if none
    var isController: Boolean, // whether the user controls the managed room
    /** Where this user's playhead was in the last `List` response, in seconds; null when unknown. */
    var position: Double? = null
)