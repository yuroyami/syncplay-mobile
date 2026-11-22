package app.room

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.preferences.PrefExtraConfig
import app.preferences.Preferences.MSG_OUTLINE_MAX
import app.preferences.Preferences.MSG_OUTLINE_THICKNESS
import app.preferences.Preferences.MSG_SHADOW_ACTIVATE
import app.room.models.MessagePalette
import app.room.models.eventColor
import app.room.models.hsl
import app.room.models.timeColor
import app.room.ui.chat.OutlineStep
import app.room.ui.chat.overVideoStyle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The style of chat lines over video: the outline in dp, the plate behind each line and the event color. */
class ChatOverVideoStyleTest {
    @Test
    fun theOutlineIsInStepsOfDp() {
        assertNull(overVideoStyle(10, outlineSteps = 0, shadow = true, plateOpacity = 0, showTime = false).outline, "Zero switches the outline off")
        assertEquals(OutlineStep * 2, overVideoStyle(10, outlineSteps = 2, shadow = true, plateOpacity = 0, showTime = false).outline)
        assertEquals(1.5.dp, OutlineStep * MSG_OUTLINE_THICKNESS.default, "The default outline is a 1.5dp stroke")
    }

    @Test
    fun aStoredValueFromThePixelScaleCountsAsTheHighestStep() {
        val old = overVideoStyle(10, outlineSteps = 30, shadow = true, plateOpacity = 0, showTime = false)
        assertEquals(OutlineStep * MSG_OUTLINE_MAX, old.outline)
        val slider = MSG_OUTLINE_THICKNESS.config?.extraConfig as PrefExtraConfig.Slider
        assertEquals(MSG_OUTLINE_MAX, slider.maxValue, "The slider ends at the highest step")
    }

    @Test
    fun theShadowIsOnByDefault() = assertTrue(MSG_SHADOW_ACTIVATE.default)

    @Test
    fun thePlateIsBlackAndAsDarkAsTheSettingSays() {
        assertNull(overVideoStyle(10, 2, shadow = true, plateOpacity = 0, showTime = false).plate, "Zero draws no plate")
        assertEquals(Color.Black, overVideoStyle(10, 2, shadow = true, plateOpacity = 255, showTime = false).plate)
        val half = overVideoStyle(10, 2, shadow = true, plateOpacity = 128, showTime = false).plate
        assertEquals(Color.Black.copy(alpha = 128 / 255f), half)
        assertEquals(Color.Black, overVideoStyle(10, 2, shadow = true, plateOpacity = 999, showTime = false).plate, "A value over 255 counts as 255")
    }

    @Test
    fun anEventLineOverVideoIsAsLightAsChatText() {
        val chat = MessagePalette()
        assertEquals(chat.systemmsgColor, chat.eventColor(overVideo = false), "On its own ground, the event color stays")
        val lifted = chat.eventColor(overVideo = true)
        assertTrue(hsl(lifted).third > hsl(chat.systemmsgColor).third, "Over video, the event line is lighter")
        assertTrue(abs(hsl(lifted).third - 0.92f) < 0.01f, "The lightness stops at the cap: ${hsl(lifted).third}")
    }

    @Test
    fun theTimeOfALineIsLighterOverVideoButDimmerThanTheText() {
        val chat = MessagePalette()
        assertEquals(chat.timestampColor, chat.timeColor(overVideo = false))
        val lifted = hsl(chat.timeColor(overVideo = true)).third
        assertTrue(lifted > hsl(chat.timestampColor).third && lifted < hsl(chat.eventColor(overVideo = true)).third, "The time: $lifted")
    }

    @Test
    fun aPickedEventColorKeepsItsHueOverVideo() {
        val blue = Color(0xFF2040A0)
        val lifted = MessagePalette(systemmsgColor = blue).eventColor(overVideo = true)
        assertTrue(abs(hsl(lifted).first - hsl(blue).first) < 2f, "The hue stays: ${hsl(lifted).first} against ${hsl(blue).first}")
        assertTrue(hsl(lifted).third > hsl(blue).third)
    }

    @Test
    fun anEventColorThatIsLightEnoughStays() {
        val dimChat = MessagePalette(systemmsgColor = Color(0xFFDDDDDD), usermsgColor = Color(0xFF808080))
        assertEquals(dimChat.systemmsgColor, dimChat.eventColor(overVideo = true), "The event line never gets darker")
    }
}
