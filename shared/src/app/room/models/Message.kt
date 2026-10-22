package app.room.models

import app.utils.generateClockstamp
import app.utils.urlHost
import kotlinx.atomicfu.atomic
import kotlin.time.Clock
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Unicode BiDi "First Strong Isolate" (U+2068). The text up to the matching [BIDI_ISOLATE_END]
 *  takes its own direction and does not reorder the text around it. */
internal const val BIDI_ISOLATE_START = "\u2068"

/** Unicode BiDi "Pop Directional Isolate" (U+2069): closes a [BIDI_ISOLATE_START] run. */
internal const val BIDI_ISOLATE_END = "\u2069"

/** Wraps a name or a title in BiDi isolates, so its direction cannot reorder the words around it. */
internal fun String.isolated(): String = BIDI_ISOLATE_START + this + BIDI_ISOLATE_END

/** Removes trailing spaces and blank lines, so chat shows text the way a person would type it. */
fun String.collapsedForChat(): String =
    lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .joinToString("\n")

/** Gives every message a stable id, so a lazy list can key on it. */
private val messageIds = atomic(0L)

/** A single chat or system message and the data needed to render it. */
data class Message(
    /** Unique per message and stable across recomposition. The chat list keys on it. */
    val id: Long = messageIds.incrementAndGet(),

    /** The sender, or null for a message that is not chat. */
    var sender: String? = null,

    /** The clock time shown next to the message, taken when the message is created. */
    var timestamp: String = generateClockstamp(),

    var content: String = "",

    /** Whether the message is a chat line or an action of the local user. */
    var isMainUser: Boolean = false,

    /** Whether the message is an error, rendered in the error color (red by default). */
    var isError: Boolean = false,

    /**
     * The people an event names, each mapped to whether it is the local user. Chat draws each name
     * in that person's name color, and finds the name by the isolates around it in [content].
     */
    val people: Map<String, Boolean> = emptyMap(),

    /** Arrival time on the wall clock, for the one-minute grouping rule. */
    val epochMs: Long = Clock.System.now().toEpochMilliseconds(),
    /** Arrival time on the monotonic clock, so the fade expiry survives a recomposition of the HUD
     *  (the controls over the video) and corrections of the wall clock. */
    val receivedAt: TimeMark = TimeSource.Monotonic.markNow(),
) {

    /** Set once the chat list showed the message with the HUD visible. A seen message does not
     *  fade in over the video. */
    var seen = false

    /** Whether the content is a GIF or image URL, for inline rendering in chat. The check drops
     *  the query string (`?token=...`) and the fragment (`#frame=...`) first: CDN URLs often
     *  carry them, and they would break a plain `endsWith` check. */
    val isImageUrl: Boolean
        get() {
            if (sender == null || !content.startsWith("http")) return false
            val path = content.substringBefore('?').substringBefore('#')
            return path.endsWith(".gif", ignoreCase = true) ||
                path.endsWith(".webp", ignoreCase = true) ||
                path.endsWith(".png", ignoreCase = true) ||
                path.endsWith(".jpg", ignoreCase = true) ||
                path.endsWith(".jpeg", ignoreCase = true)
        }

    /** The host of the image URL, for the "load this?" line and [isFromTrustedImageHost]. */
    val imageHost: String
        get() = urlHost(content) ?: ""

    /**
     * Whether this image may load without a tap.
     *
     * An image that loads as soon as a peer posts it sends the IP address of every device in the
     * room (the group of people watching together) to a host that the peer chose. The GIF
     * picker's own CDN is exempt, because the sender picked the GIF inside this app. An image
     * from any other host waits for a tap.
     */
    val isFromTrustedImageHost: Boolean
        get() = TRUSTED_IMAGE_HOSTS.any { imageHost == it || imageHost.endsWith(".$it") }

    companion object {
        private val TRUSTED_IMAGE_HOSTS = listOf("klipy.com", "klipy.co")
    }
}
