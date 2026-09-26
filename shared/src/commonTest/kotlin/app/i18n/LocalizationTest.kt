package app.i18n

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The app language is runtime state, so a switch needs no restart. These tests cover the parts that
 * are not visual: the switch, the fallback, and the choice of plural form.
 */
class LocalizationTest {

    @AfterTest
    fun restore() = Localization.apply("en")

    @Test
    fun `switching the language changes what the app says`() {
        Localization.apply("en")
        val english = Localization.strings.cancel
        Localization.apply("de")
        assertNotEquals(english, Localization.strings.cancel, "German should not read like English")
        Localization.apply("en")
        assertEquals(english, Localization.strings.cancel)
    }

    @Test
    fun `a region tag falls back to its language`() {
        Localization.apply("de-AT")
        assertEquals(appStrings[Locales.De]?.cancel, Localization.strings.cancel)
    }

    @Test
    fun `a language we do not ship falls back to English`() {
        Localization.apply("is-IS")
        assertEquals(EnAppStrings.cancel, Localization.strings.cancel)
    }

    @Test
    fun `a blank preference follows the device`() {
        Localization.apply("")
        assertTrue(Localization.lyricist.languageTag.isNotBlank())
    }

    @Test
    fun `an untranslated key still says something`() {
        Localization.apply("pl")
        assertTrue(Localization.strings.cancel.isNotBlank())
    }

    @Test
    fun `english counts one thing apart from the rest`() {
        assertEquals(PluralForm.One, pluralForm("en", 1))
        assertEquals(PluralForm.Other, pluralForm("en", 0))
        assertEquals(PluralForm.Other, pluralForm("en", 21))
    }

    @Test
    fun `russian and polish use three forms`() {
        assertEquals(PluralForm.One, pluralForm("ru", 21))
        assertEquals(PluralForm.Few, pluralForm("ru", 22))
        assertEquals(PluralForm.Many, pluralForm("ru", 25))
        assertEquals(PluralForm.Many, pluralForm("ru", 11))

        assertEquals(PluralForm.One, pluralForm("pl", 1))
        assertEquals(PluralForm.Many, pluralForm("pl", 21))
        assertEquals(PluralForm.Few, pluralForm("pl", 22))
        assertEquals(PluralForm.Many, pluralForm("pl", 25))
    }

    @Test
    fun `arabic counts zero one and two on their own`() {
        assertEquals(PluralForm.Zero, pluralForm("ar", 0))
        assertEquals(PluralForm.One, pluralForm("ar", 1))
        assertEquals(PluralForm.Two, pluralForm("ar", 2))
        assertEquals(PluralForm.Few, pluralForm("ar", 3))
        assertEquals(PluralForm.Many, pluralForm("ar", 11))
        assertEquals(PluralForm.Other, pluralForm("ar", 100))
    }

    @Test
    fun `french groups zero with one and chinese has one form`() {
        assertEquals(PluralForm.One, pluralForm("fr", 0))
        assertEquals(PluralForm.One, pluralForm("fr", 1))
        assertEquals(PluralForm.Other, pluralForm("fr", 2))
        assertEquals(PluralForm.Other, pluralForm("zh", 1))
    }

    @Test
    fun `a device language we do not ship counts with the English rule`() {
        // Japanese has one form for every number, and Ukrainian picks "one" for 21.
        Localization.apply("ja-JP")
        assertEquals("1 user", Localization.strings.roomUserCount(1))
        Localization.apply("uk-UA")
        assertEquals("21 users", Localization.strings.roomUserCount(21))
    }

    @Test
    fun `a shipped language keeps its own rule under a region tag`() {
        assertEquals(Locales.Ru, shownLanguage("ru-RU"))
        assertEquals(Locales.En, shownLanguage("ja"))
        assertEquals(Locales.Fr, shownLanguage("fr_CA"))
    }

    @Test
    fun `the room count reads correctly at both ends`() {
        Localization.apply("en")
        assertEquals("1 user", Localization.strings.roomUserCount(1))
        assertEquals("4 users", Localization.strings.roomUserCount(4))
    }
}
