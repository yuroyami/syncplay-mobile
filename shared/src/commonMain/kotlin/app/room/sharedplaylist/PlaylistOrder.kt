package app.room.sharedplaylist

/** The list with the entry at [from] moved to [to]. The entries between them shift by one. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }

/** Where the entry at this index sits after the entry at [from] moves to [to]. A negative index stays. */
internal fun Int.afterMove(from: Int, to: Int): Int = when {
    this == from -> to
    from < to && this in (from + 1)..to -> this - 1
    to < from && this in to until from -> this + 1
    else -> this
}

/**
 * A room that did not survive a dropped connection: its first list afterwards is empty and set by
 * nobody. A person who clears the list sends their name with it.
 */
internal fun cameBackEmpty(before: List<String>, after: List<String>, setBy: String): Boolean =
    before.isNotEmpty() && after.isEmpty() && setBy.isBlank()
