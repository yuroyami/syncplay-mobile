package app.utils

import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The one clock that the sync core and the built-in server read.
 *
 * Time-dependent code in `app.protocol` and `app.server` reads this clock instead of
 * [Clock.System] or [generateTimestampMillis]. So a test can install a clock that it controls:
 * "wait a second" becomes "advance a second", and nothing sleeps.
 *
 * Production code never installs a clock. UI, logging and file naming use the wall clock
 * directly, because no test checks them.
 */
object SyncClock {

    @Volatile
    private var source: () -> Long = { generateTimestampMillis() }

    /** Milliseconds since the epoch. */
    fun nowMillis(): Long = source()

    /** Seconds since the epoch, full precision. The protocol uses seconds. */
    fun nowSeconds(): Double = source() / 1000.0

    /** The current instant as an [Instant], for code that does date arithmetic. */
    fun now(): Instant = Instant.fromEpochMilliseconds(source())

    /**
     * Replaces the clock source. For tests only, and always paired with [reset] in teardown.
     */
    internal fun installForTest(clock: () -> Long) {
        source = clock
    }

    /** Restores the wall clock. */
    internal fun reset() {
        source = { generateTimestampMillis() }
    }
}

/**
 * A clock that a test moves by hand. It starts at a fixed instant, so a failure reads the same on
 * every machine.
 */
internal class TestClock(private var millis: Long = 1_700_000_000_000L) {
    fun install() = SyncClock.installForTest { millis }
    fun advanceMillis(delta: Long) { millis += delta }
    fun advanceSeconds(delta: Double) { millis += (delta * 1000).toLong() }
    fun nowMillis(): Long = millis
}
