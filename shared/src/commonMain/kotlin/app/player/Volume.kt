package app.player

import app.utils.platformCallback
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The volume scale of one engine, from 0 to [max], in two ranges.
 *
 * 0 to 100 is the base. The base is the device's music volume where the platform lets the app
 * set it (Android). Elsewhere, the base is the engine's own output (iOS has no public API for
 * the system volume). Above 100 is the engine's gain, where the engine can amplify: VLCKit and
 * mpv go to 200 natively, ExoPlayer goes to 200 through a loudness effect, and KitePlayer and
 * AVPlayer stop at 100.
 */
class VolumeLadder(val deviceSteps: Int, val gainMax: Int) {
    /** True where the platform lets the app set the device's music volume. */
    val deviceOwnsBase: Boolean get() = deviceSteps > 0
    val max: Int get() = gainMax.coerceAtLeast(BASE_MAX)
    val hasGain: Boolean get() = gainMax > BASE_MAX

    companion object {
        const val BASE_MAX = 100
    }
}

/**
 * Reads and writes the [VolumeLadder] position of one engine. The base goes to the device volume
 * or the engine output, and the gain goes to the engine.
 */
class VolumeController(private val player: PlayerImpl) {

    /* Volatile: the pointer handler reads it during a swipe, and composition reads it too. */
    @Volatile
    private var cachedLadder: VolumeLadder? = null

    @Volatile
    private var cachedAt: TimeSource.Monotonic.ValueTimeMark? = null

    /**
     * The ladder, rebuilt at most once per [LADDER_TTL].
     *
     * Building it asks the platform how many steps the device volume has. On Android, that is a
     * binder call (a call into another process) to the audio service. A volume swipe reads the
     * ladder on every pointer sample and then writes through [set]. Without the cache, one finger
     * movement would cross the process boundary three or four times. None of these values change
     * faster than a headset gets plugged in.
     */
    val ladder: VolumeLadder
        get() {
            val cached = cachedLadder
            val at = cachedAt
            // Monotonic time, not the wall clock. A wall clock that steps backwards gives a negative
            // elapsed time, which reads as "still fresh" and freezes the ladder until it catches up.
            if (cached != null && at != null && at.elapsedNow() < LADDER_TTL) return cached
            return VolumeLadder(
                deviceSteps = platformCallback.deviceVolumeSteps(),
                gainMax = player.gainMax,
            ).also {
                cachedLadder = it
                cachedAt = TimeSource.Monotonic.markNow()
            }
        }

    /** The ladder position, 0 to [VolumeLadder.max]: the base, or the gain once the base is full. */
    fun current(): Int {
        val ladder = ladder
        val gain = if (ladder.hasGain) player.getGain() else VolumeLadder.BASE_MAX
        if (gain > VolumeLadder.BASE_MAX) return gain.coerceAtMost(ladder.max)
        return base(ladder).coerceIn(0, VolumeLadder.BASE_MAX)
    }

    /** Sets the ladder position. The base fills before any gain applies, and the gain drops first. */
    fun set(percent: Int) {
        val ladder = ladder
        val target = percent.coerceIn(0, ladder.max)
        if (target <= VolumeLadder.BASE_MAX) {
            if (ladder.hasGain) player.setGain(VolumeLadder.BASE_MAX)
            setBase(ladder, target)
        } else {
            setBase(ladder, VolumeLadder.BASE_MAX)
            player.setGain(target)
        }
    }

    private fun base(ladder: VolumeLadder): Int {
        if (!ladder.deviceOwnsBase) return player.getEngineVolume()
        val steps = ladder.deviceSteps.coerceAtLeast(1)
        return platformCallback.getDeviceVolume() * VolumeLadder.BASE_MAX / steps
    }

    private fun setBase(ladder: VolumeLadder, percent: Int) {
        if (!ladder.deviceOwnsBase) {
            player.setEngineVolume(percent)
            return
        }
        // The engine's own output stays at full, so only the device volume changes what is heard.
        player.setEngineVolume(VolumeLadder.BASE_MAX)
        val steps = ladder.deviceSteps.coerceAtLeast(1)
        platformCallback.setDeviceVolume((percent * steps + VolumeLadder.BASE_MAX / 2) / VolumeLadder.BASE_MAX)
    }

    private companion object {
        /** Long enough to cover a whole swipe, short enough that a headset plug-in shows up soon. */
        val LADDER_TTL = 500.milliseconds
    }
}
