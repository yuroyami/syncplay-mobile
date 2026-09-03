package app.room.models

import app.utils.generateClockstamp
import kotlin.time.Clock

/** Unicode BiDi "First Strong Isolate" (U+2068): text until the matching PDI resolves its
 *  direction on its own without reordering surrounding text. */
internal const val BIDI_ISOLATE_START = "\u2068"

/** Unicode BiDi "Pop Directional Isolate" (U+2069): closes a [BIDI_ISOLATE_START] run. */
internal const val BIDI_ISOLATE_END = "\u2069"

/** A single chat or system message and the data needed to render it. */
data class Message(
    /** The sender of the message. Null when it's not a chat message */
    var sender: String? = null,

    /** The timestamp at which the message is sent/declared */
    var timestamp: String = generateClockstamp(),

    /** Content of the message */
    var content: String = "",

    /** If the message refers to a chat/action by the app user themself */
    var isMainUser: Boolean = false,

    /** Whether the message is an error, rendered in the error color (red by default). */
    var isError: Boolean = false,

    /** Arrival time on the wall clock, for the one-minute grouping rule. */
    val epochMs: Long = Clock.System.now().toEpochMilliseconds(),
) {

    /** Whether this message has been seen. */
    var seen = false

    /** Whether the message content is a GIF/image URL (for inline rendering in chat).
     *  CDN URLs commonly have query strings (`?token=...`) or fragments (`#frame=...`) that
     *  would defeat a naive `endsWith` check on the raw content — strip them before testing. */
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

    /** The host an image came from, for the "load this?" line and the trust check below. */
    val imageHost: String
        get() = content.substringAfter("://", "").substringBefore('/').substringBefore(':').lowercase()

    /**
     * Whether this image may load without being asked for.
     *
     * Fetching an image the moment a peer names it hands that peer's chosen host the address of
     * every device in the room, with nothing asked. The GIF picker's own CDN is exempt, because
     * the sender chose it from inside this app; anything else waits for a tap.
     */
    val isFromTrustedImageHost: Boolean
        get() = TRUSTED_IMAGE_HOSTS.any { imageHost == it || imageHost.endsWith(".$it") }

    companion object {
        private val TRUSTED_IMAGE_HOSTS = listOf("klipy.com", "klipy.co")
    }
}
