package app.room.models

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Each person in chat gets a shade of the friend colour. The name picks the shade, and people in
 * one room move apart when their shades are the same or next to each other. The local user keeps
 * the self colour.
 */
class PersonShadeTest {
    private val chat = MessagePalette()
    private val base = hsl(chat.friendtagColor)

    /** The hue turn of [color] from the friend colour, in degrees from -180 to 180. */
    private fun turnOf(color: Color): Float = ((hsl(color).first - base.first + 540f) % 360f) - 180f

    private fun said(sender: String, self: Boolean = false) = Message(sender = sender, content = "hi", isMainUser = self)

    private fun named(vararg people: Pair<String, Boolean>) = Message(content = "an event", people = mapOf(*people))

    @Test
    fun theLocalUserKeepsTheSelfColour() {
        assertEquals(chat.selftagColor, chat.nameColor("Alice", self = true))
        assertEquals(chat.selftagColor, chat.nameColor("Alice", self = true, shades = mapOf("Alice" to 3)))
    }

    @Test
    fun aNameKeepsItsShade() {
        // The hash is part of the look: a change here gives every person in every room a new colour.
        assertEquals(1, preferredShade("Sam"))
        assertEquals(3, preferredShade("Leo"))
        val leo = chat.nameColor("Leo", self = false)
        assertTrue(abs(turnOf(leo) - 24f) < 0.5f, "Leo turns the hue by 24 degrees, not ${turnOf(leo)}")
        assertTrue(abs(hsl(leo).third - (base.third - 0.1f)) < 0.005f, "Leo is 0.1 darker")
    }

    @Test
    fun theMiddleShadeIsTheFriendColour() {
        val (hue, saturation, lightness) = hsl(shadeOf(chat.friendtagColor, 7))
        assertTrue(abs(hue - base.first) < 0.5f && abs(saturation - base.second) < 0.01f && abs(lightness - base.third) < 0.005f)
    }

    @Test
    fun everyShadeStaysNearTheFriendColour() {
        for (shade in 0 until SHADES) {
            val color = shadeOf(chat.friendtagColor, shade)
            val (_, saturation, lightness) = hsl(color)
            assertTrue(abs(turnOf(color)) <= 48.5f, "Shade $shade turns the hue by ${turnOf(color)} degrees")
            assertTrue(abs(lightness - base.third) <= 0.105f, "Shade $shade moves the lightness to $lightness")
            assertTrue(abs(saturation - base.second) < 0.01f, "Shade $shade keeps the saturation")
            assertEquals(chat.friendtagColor.alpha, color.alpha, "Shade $shade keeps the alpha")
        }
    }

    @Test
    fun aRoomGivesTheShadeInItsMap() {
        val shades = mapOf("Leo" to 12)
        assertEquals(shadeOf(chat.friendtagColor, 12), chat.nameColor("Leo", self = false, shades = shades))
    }

    @Test
    fun peopleWithTheSameShadeMoveApart() {
        // All four names pick shade 4.
        val names = listOf("Max", "Ray", "Vic", "Zoe")
        assertTrue(names.all { preferredShade(it) == 4 })
        val shades = personShades(names.map { said(it) })
        assertEquals(4, shades["Max"], "The first person keeps the shade that the name picks")
        for (a in names) for (b in names) {
            if (a < b) assertTrue(shadeDistance(shades.getValue(a), shades.getValue(b)) >= 2, "$a and $b stay two steps apart")
        }
    }

    @Test
    fun peopleWhoseShadesAreApartKeepThem() {
        // Sam picks shade 1 and Leo shade 3, two hue turns apart.
        assertEquals(mapOf("Sam" to 1, "Leo" to 3), personShades(listOf(said("Sam"), said("Leo"))))
    }

    @Test
    fun noTwoPeopleShareAShadeWhileShadesAreLeft() {
        val names = listOf("Pam", "Tom", "Yan", "Cat", "Wes", "Sam", "Fay", "Oli", "Leo", "Max", "Ray", "Vic", "Zoe", "Ann", "Bob")
        val shades = personShades(names.map { said(it) })
        assertEquals(SHADES, shades.values.toSet().size)
    }

    @Test
    fun aLaterMessageNeverMovesAShade() {
        val messages = listOf(said("Max"), named("Ray" to false), said("Vic"), said("Max"), said("Zoe"), said("Kai"))
        val full = personShades(messages)
        for (count in messages.indices) {
            for ((name, shade) in personShades(messages.take(count))) assertEquals(shade, full[name], "$name keeps its shade")
        }
    }

    @Test
    fun theLocalUserTakesNoShade() {
        val shades = personShades(listOf(said("Me", self = true), named("Me" to true, "Kai" to false)))
        assertFalse("Me" in shades)
        assertEquals(preferredShade("Kai"), shades["Kai"], "A person named only by an event takes a shade too")
    }
}
