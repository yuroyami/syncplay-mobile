package app.room.sharedplaylist

/** Returns a copy with the entry at [from] moved to [to]. The entries between them shift by one. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }

/**
 * Returns where the entry at this index sits after the entry at [from] moves to [to]. A negative
 * index stays as it is.
 */
internal fun Int.afterMove(from: Int, to: Int): Int = when {
    this == from -> to
    from < to && this in (from + 1)..to -> this - 1
    to < from && this in to until from -> this + 1
    else -> this
}

/**
 * Whether the room (the group of people watching together) lost its playlist in a dropped
 * connection: the first list after the drop is empty and set by nobody. A user who clears the
 * list sends their name with the change.
 */
internal fun cameBackEmpty(before: List<String>, after: List<String>, setBy: String): Boolean =
    before.isNotEmpty() && after.isEmpty() && setBy.isBlank()
