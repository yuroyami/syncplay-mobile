package app.protocol.sync

/**
 * The playback time offset, in one place.
 *
 * Someone whose copy of a film carries an extra ten seconds of intro sets the offset to +10, and
 * from then on their position means something different from everyone else's. Every conversion
 * between the two has to go through here, or one path applies the offset and another does not and
 * the room drifts by exactly the amount the offset was meant to remove.
 */

/** Our copy's time from the room's time. The offset is how far our copy runs ahead. */
fun roomToLocalMs(roomMs: Double, offsetSeconds: Double): Double = roomMs + offsetSeconds * 1000.0

/** What the room should hear about our copy's time. */
fun localToRoomSeconds(localMs: Long, offsetSeconds: Double): Double = localMs / 1000.0 - offsetSeconds
