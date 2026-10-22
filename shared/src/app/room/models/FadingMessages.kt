package app.room.models

import kotlin.time.Duration

internal fun fadingMessages(
    messages: List<Message>, hold: Duration, maxCount: Int, muted: Set<String>,
): List<Message> = messages.filter {
    !it.isMainUser && !it.seen && it.sender !in muted && it.receivedAt.elapsedNow() < hold
}.takeLast(maxCount.coerceAtLeast(0))
