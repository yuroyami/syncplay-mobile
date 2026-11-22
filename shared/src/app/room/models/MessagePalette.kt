package app.room.models

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * The colors that chat draws with, one per chat color setting. The defaults below belong to chat.
 * No theme reads or changes them, and only the chat color settings override them.
 */
data class MessagePalette(
    val timestampColor: Color = Color(0xFF737373),
    val selftagColor: Color = Color(0xFF9879EF),
    val friendtagColor: Color = Color(0xFF6ECB5A),
    val systemmsgColor: Color = Color(0xFFA3A3A3),
    val usermsgColor: Color = Color.White,
    val errormsgColor: Color = Color(0xFFE85455),
    val includeTimestamp: Boolean = true,
)

/**
 * The shade of each person in the chat of the room, by name (see [personShades]). The room screen
 * provides it, so every chat view gives a person the same shade. Without it, each name takes the
 * shade that it picks ([preferredShade]).
 */
val LocalPersonShades = compositionLocalOf<Map<String, Int>> { emptyMap() }

/**
 * The color of [name] in chat: its name, its dash and its mentions in event lines. The local user
 * ([self]) keeps the self tag color. Every other person gets a shade of the friend tag color
 * ([shadeOf]): the one in [shades], or else the one that the name picks. So several people stay
 * apart, and chat keeps the color of the settings.
 */
fun MessagePalette.nameColor(name: String, self: Boolean, shades: Map<String, Int> = emptyMap()): Color =
    if (self) selftagColor else shadeOf(friendtagColor, shades[name] ?: preferredShade(name))

/**
 * The lightness that an event line gets at most when it is lifted over video. Under 1, so an
 * event color with a hue keeps that hue.
 */
private const val EVENT_LIGHTNESS_CAP = 0.92f

/** The lightness that the time of a line gets at most over video. The time stays dimmer than the text. */
private const val TIME_LIGHTNESS_CAP = 0.8f

/**
 * The color of a neutral event line. [overVideo], the line is at least as light as chat text, up
 * to [EVENT_LIGHTNESS_CAP], because the dimmer event color is hard to read on a busy picture. The
 * hue and the saturation of the event color stay. On a ground of its own, the line keeps the event
 * color as it is.
 */
fun MessagePalette.eventColor(overVideo: Boolean): Color =
    if (overVideo) lifted(systemmsgColor, EVENT_LIGHTNESS_CAP) else systemmsgColor

/** The color of the time of a line. [overVideo], it is lifted as an event line is, up to [TIME_LIGHTNESS_CAP]. */
fun MessagePalette.timeColor(overVideo: Boolean): Color =
    if (overVideo) lifted(timestampColor, TIME_LIGHTNESS_CAP) else timestampColor

/** [color] at least as light as chat text, up to [cap]. A color that is light enough stays as it is. */
private fun MessagePalette.lifted(color: Color, cap: Float): Color {
    val (hue, saturation, lightness) = hsl(color)
    val target = minOf(hsl(usermsgColor).third, cap)
    return if (lightness >= target) color else Color.hsl(hue, saturation, target, color.alpha)
}

/** The hue turns of the shades, in degrees. */
private val HUE_TURNS = floatArrayOf(-48f, -24f, 0f, 24f, 48f)

/** The lightness steps of the shades. */
private val LIGHTNESS_STEPS = floatArrayOf(-0.1f, 0f, 0.1f)

/** The number of shades: each hue turn at each lightness step. Shade 7 is the friend tag color itself. */
internal val SHADES = HUE_TURNS.size * LIGHTNESS_STEPS.size

/**
 * The shade that [name] picks, through an FNV-1a hash of its UTF-16 units. The hash gives the same
 * shade on every platform and in every session.
 */
internal fun preferredShade(name: String): Int {
    var hash = 0x811C9DC5.toInt()
    for (unit in name) hash = (hash xor unit.code) * 0x01000193
    return (hash ushr 1) % SHADES
}

/** Shade [shade] of [base]: the hue turns by up to 48 degrees, the lightness moves by up to 0.1, and the saturation stays. */
internal fun shadeOf(base: Color, shade: Int): Color {
    val turn = HUE_TURNS[shade % HUE_TURNS.size]
    val step = LIGHTNESS_STEPS[shade / HUE_TURNS.size]
    val (hue, saturation, lightness) = hsl(base)
    return Color.hsl(
        hue = (hue + turn + 360f) % 360f,
        saturation = saturation,
        lightness = (lightness + step).coerceIn(0f, 1f),
        alpha = base.alpha,
    )
}

/** How far apart two shades are: the hue turns plus the lightness steps between them. */
internal fun shadeDistance(a: Int, b: Int): Int =
    abs(a % HUE_TURNS.size - b % HUE_TURNS.size) + abs(a / HUE_TURNS.size - b / HUE_TURNS.size)

/**
 * The shade of each person that [messages] name, the local user left out. People take shades in
 * the order in which they first appear. A person takes the shade that the name picks
 * ([preferredShade]), unless a person before took that shade or one next to it. Then the person
 * takes the shade farthest from all taken shades, so up to four people always stay two steps
 * apart. A later message never moves the shade of a person who came before.
 */
internal fun personShades(messages: List<Message>): Map<String, Int> {
    val shades = LinkedHashMap<String, Int>()
    fun meet(name: String) {
        if (name !in shades) shades[name] = freeShade(preferredShade(name), shades.values)
    }
    for (message in messages) {
        val sender = message.sender
        if (sender != null && !message.isMainUser) meet(sender)
        for ((person, self) in message.people) if (!self) meet(person)
    }
    return shades
}

/** [preferred] when no [taken] shade is the same or next to it. Else the shade farthest from all taken shades, and of those the nearest to [preferred]. */
private fun freeShade(preferred: Int, taken: Collection<Int>): Int {
    if (taken.all { shadeDistance(it, preferred) >= 2 }) return preferred
    return (0 until SHADES).maxWith(
        compareBy<Int>({ shade -> taken.minOf { shadeDistance(it, shade) } }, { shade -> -shadeDistance(shade, preferred) }, { shade -> -shade }),
    )
}

/** The hue (0 to 360), saturation and lightness (0 to 1) of an sRGB [color]. */
internal fun hsl(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f
    val delta = max - min
    if (delta == 0f) return Triple(0f, 0f, lightness)
    val saturation = (delta / (1f - abs(2f * lightness - 1f))).coerceIn(0f, 1f)
    val hue = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    return Triple((hue + 360f) % 360f, saturation, lightness)
}
