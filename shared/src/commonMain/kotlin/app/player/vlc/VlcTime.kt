package app.player.vlc

/**
 * Converts a libVLC time or length to milliseconds. Since 4.0.0a20, libVLC reports both in
 * microseconds. This rounds to the nearest millisecond, as libVLC 4.0.0a19 did. A negative value
 * means "no value" and stays -1.
 */
internal fun vlcMillisFromMicros(us: Long): Long {
    if (us < 0L) return -1L
    // Same result as (us + 500) / 1000, without the overflow near Long.MAX_VALUE.
    return us / 1_000L + if (us % 1_000L >= 500L) 1L else 0L
}
