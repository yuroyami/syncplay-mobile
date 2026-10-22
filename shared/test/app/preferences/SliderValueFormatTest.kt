package app.preferences

import app.room.ui.chat.MessageStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class SliderValueFormatTest {
    private fun slider(pref: Pref<Int>) = pref.config!!.extraConfig as PrefExtraConfig.Slider

    @Test
    fun offsetDisplaysSignedSecondsAroundItsStoredZero() {
        val offset = slider(Preferences.USER_TIME_OFFSET)
        assertEquals("s", offset.unit)
        assertEquals("-60.0", offset.formatValue(offset.minValue))
        assertEquals("-0.1", offset.formatValue(599))
        assertEquals("0.0", offset.formatValue(Preferences.USER_TIME_OFFSET.default))
        assertEquals("+0.1", offset.formatValue(601))
        assertEquals("+10.0", offset.formatValue(700))
        assertEquals("+60.0", offset.formatValue(offset.maxValue))
    }

    @Test
    fun syncThresholdDefaultsDisplaySecondsRatherThanStoredTenths() {
        for ((pref, expected) in listOf(
            Preferences.SYNC_REWIND_THRESHOLD to "4.0",
            Preferences.SYNC_SLOWDOWN_THRESHOLD to "1.5",
            Preferences.SYNC_FASTFORWARD_THRESHOLD to "5.0",
        )) {
            assertEquals("s", slider(pref).unit)
            assertEquals(expected, slider(pref).formatValue(pref.default))
        }
    }

    @Test
    fun chatSizeCanReachFiveAndDefaultsToTen() {
        val pref = Preferences.MSG_FONTSIZE
        val range = slider(pref)
        assertEquals(5, range.minValue)
        assertEquals(10, pref.default)
        assertEquals(5, MessageStyle(range.minValue, null, false, false).fontSize)
        assertEquals(10, MessageStyle(pref.default, null, false, false).fontSize)
        assertEquals(24, MessageStyle(range.maxValue, null, false, false).fontSize)
    }
}
