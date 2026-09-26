package app.player.vlc

import kotlin.test.Test
import kotlin.test.assertEquals

class VlcBufferingReleaseTest {

    private fun run(buffering: Boolean, vararg clock: Long): List<Boolean> {
        val release = VlcBufferingRelease()
        return clock.map { release.onSample(buffering, it) }
    }

    @Test
    fun twoForwardSamplesWhileBufferingClearTheFlag() {
        assertEquals(listOf(false, false, false, false, true), run(true, 61_961, 61_961, 61_961, 62_211, 62_461))
    }

    @Test
    fun aStandingOrBackwardClockStartsOver() {
        assertEquals(listOf(false, false, false, false, false, true), run(true, 1_000, 1_250, 1_250, 900, 1_150, 1_400))
    }

    @Test
    fun nothingClearsWhileTheFlagIsOff() {
        assertEquals(listOf(false, false, false), run(false, 1_000, 1_250, 1_500))
    }

    @Test
    fun aMissingClockStartsOver() {
        assertEquals(listOf(false, false, false, false, false, true), run(true, 1_000, 1_250, -1, 1_500, 1_750, 2_000))
    }
}
