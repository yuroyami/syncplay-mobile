package app.utils

import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The one clock the sync core and the hosted server read.
 *
 * Everything time-dependent in `app.protocol` and `app.server` goes through here instead of
 * calling [Clock.System] or [generateTimestampMillis] directly. That is what makes those parts
 * constructible in a test: a test installs a clock it controls, so "wait a second" becomes
 * "advance a second" and nothing sleeps.
 *
 * Production code never installs anything. UI, logging and file naming still use the wall clock
 * directly, because nothing asserts on them.
 */
object SyncClock {

    @Volatile
    private var source: () -> Long = { generateTimestampMillis() }

    /** Milliseconds since the epoch. */
    fun nowMillis(): Long = source()

    /** Seconds since the epoch, full precision. The protocol talks in these. */
    fun nowSeconds(): Double = source() / 1000.0

    /** The same instant, for the code that does date arithmetic on it. */
    fun now(): Instant = Instant.fromEpochMilliseconds(source())

    /**
     * Point the clock somewhere else. Tests only, and always paired with [reset] in teardown.
     */
    internal fun installForTest(clock: () -> Long) {
        source = clock
    }

    /** Back to the wall clock. */
    internal fun reset() {
        source = { generateTimestampMillis() }
    }
}

/**
 * A clock a test drives by hand. Starts at an arbitrary but fixed instant so failures read the
 * same on every machine.
 */
internal class TestClock(private var millis: Long = 1_700_000_000_000L) {
    fun install() = SyncClock.installForTest { millis }
    fun advanceMillis(delta: Long) { millis += delta }
    fun advanceSeconds(delta: Double) { millis += (delta * 1000).toLong() }
    fun nowMillis(): Long = millis
}
