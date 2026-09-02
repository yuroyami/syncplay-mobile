package app.player

import app.utils.platformCallback

/**
 * One volume ladder with two rungs.
 *
 * 0 to 100 is the base: the device's own music volume where the platform lets the app set it
 * (Android), else the engine's own output (iOS, desktop; iOS has no public API for the system
 * volume). Above 100 is the engine's gain, where the engine can amplify: VLCKit and mpv go to
 * 200 natively, ExoPlayer to 200 through a loudness effect, KitePlayer and AVPlayer stop at 100.
 */
class VolumeLadder(val deviceOwnsBase: Boolean, val gainMax: Int) {
    val max: Int get() = gainMax.coerceAtLeast(BASE_MAX)
    val hasGain: Boolean get() = gainMax > BASE_MAX

    companion object {
        const val BASE_MAX = 100
    }
}

/** Reads and writes the ladder for one engine, routing the base and the gain to where they live. */
class VolumeController(private val player: PlayerImpl) {

    val ladder: VolumeLadder
        get() = VolumeLadder(deviceOwnsBase = platformCallback.deviceVolumeSteps() > 0, gainMax = player.gainMax)

    /** The ladder position, 0 to [VolumeLadder.max]: the base, or the gain once the base is full. */
    fun current(): Int {
        val ladder = ladder
        val gain = if (ladder.hasGain) player.getGain() else VolumeLadder.BASE_MAX
        if (gain > VolumeLadder.BASE_MAX) return gain.coerceAtMost(ladder.max)
        return base(ladder).coerceIn(0, VolumeLadder.BASE_MAX)
    }

    /** Sets the ladder position; the base fills before any gain is applied, and gain drops first. */
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
        val steps = platformCallback.deviceVolumeSteps().coerceAtLeast(1)
        return platformCallback.getDeviceVolume() * VolumeLadder.BASE_MAX / steps
    }

    private fun setBase(ladder: VolumeLadder, percent: Int) {
        if (!ladder.deviceOwnsBase) {
            player.setEngineVolume(percent)
            return
        }
        // The engine's own output stays at full so the device's stream is the only thing heard moving.
        player.setEngineVolume(VolumeLadder.BASE_MAX)
        val steps = platformCallback.deviceVolumeSteps().coerceAtLeast(1)
        platformCallback.setDeviceVolume((percent * steps + VolumeLadder.BASE_MAX / 2) / VolumeLadder.BASE_MAX)
    }
}
