package app.i18n

/**
 * The plural form that a language uses for a number. The names come from CLDR (the Unicode
 * locale data), and the `_zero` / `_one` / `_two` / `_few` / `_many` / `_other` string keys
 * use the same names.
 */
enum class PluralForm { Zero, One, Two, Few, Many, Other }

/**
 * The plural form that [count] takes in [language]. The rules cover the eight languages that
 * the app ships and a few more. Any other language uses the English rule.
 *
 * These are the integer rules from CLDR. They are short because the app only counts whole
 * things: people in a room, clients on a server.
 */
fun pluralForm(language: String, count: Int): PluralForm {
    val n = if (count < 0) -count else count
    return when (language.substringBefore('-').substringBefore('_').lowercase()) {
        // One form for every number.
        "zh", "ja", "ko", "vi", "th", "id", "ms" -> PluralForm.Other

        // Zero and one share a form. Every other number is plural.
        "fr" -> if (n <= 1) PluralForm.One else PluralForm.Other

        "ru", "uk" -> when {
            n % 10 == 1 && n % 100 != 11 -> PluralForm.One
            n % 10 in 2..4 && n % 100 !in 12..14 -> PluralForm.Few
            else -> PluralForm.Many
        }

        "pl" -> when {
            n == 1 -> PluralForm.One
            n % 10 in 2..4 && n % 100 !in 12..14 -> PluralForm.Few
            else -> PluralForm.Many
        }

        "ar" -> when {
            n == 0 -> PluralForm.Zero
            n == 1 -> PluralForm.One
            n == 2 -> PluralForm.Two
            n % 100 in 3..10 -> PluralForm.Few
            n % 100 in 11..99 -> PluralForm.Many
            else -> PluralForm.Other
        }

        // English, German, Spanish and anything unknown.
        else -> if (n == 1) PluralForm.One else PluralForm.Other
    }
}

/** Picks one of the six forms for [count], by the rule for the current Lyricist language tag. */
internal fun <T> plural(
    count: Int,
    zero: T, one: T, two: T, few: T, many: T, other: T,
): T = when (pluralForm(Localization.lyricist.languageTag, count)) {
    PluralForm.Zero -> zero
    PluralForm.One -> one
    PluralForm.Two -> two
    PluralForm.Few -> few
    PluralForm.Many -> many
    PluralForm.Other -> other
}

/** "3 users", in the room's status line. */
fun AppStrings.roomUserCount(count: Int): String = plural(
    count,
    zero = roomUserCountZero, one = roomUserCountOne, two = roomUserCountTwo,
    few = roomUserCountFew, many = roomUserCountMany, other = roomUserCountOther,
)(count)

/** "3 people connected", in the hosting panel. */
fun AppStrings.serverHostClients(count: Int): String = plural(
    count,
    zero = serverHostClientsZero, one = serverHostClientsOne, two = serverHostClientsTwo,
    few = serverHostClientsFew, many = serverHostClientsMany, other = serverHostClientsOther,
)(count)

/** The hosting notification, which names the port as well as the count. */
fun AppStrings.serverNotificationText(port: Int, clients: Int): String = plural(
    clients,
    zero = serverNotificationTextZero, one = serverNotificationTextOne, two = serverNotificationTextTwo,
    few = serverNotificationTextFew, many = serverNotificationTextMany, other = serverNotificationTextOther,
)(port, clients)
