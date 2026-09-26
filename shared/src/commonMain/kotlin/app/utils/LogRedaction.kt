package app.utils

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Hides who was in a session. The names, rooms, files, hosts and addresses that reach the log are
 * replaced with stable placeholders such as `<user-2>`, so a reader can still follow a session
 * without learning who was in it. [loggy] applies it to every line.
 *
 * Code registers each value when it learns it, before the value can reach a log line: the join
 * details, every message from the server, a loaded file. IP addresses and the values in a URL's
 * query, such as a search text or the GIF service's customer id, are found by pattern.
 */
object LogRedactor {

    /** What a value is. The placeholder names it, so `<file-3>` reads as a file. */
    enum class Kind(val tag: String) { User("user"), Room("room"), File("file"), Host("host"), Search("search") }

    /** Shorter values are too common to replace without destroying the line around them. */
    private const val MIN_LENGTH = 3

    private val lock = SynchronizedObject()
    private val placeholders = HashMap<String, String>()
    private val counters = HashMap<String, Int>()
    private var matcher: Regex? = null

    private val ipv4 = Regex("""(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?![\d.])""")
    // A bracketed IPv6 address, as the JVM prints one. It needs "::" or six colons, so "[12:34:56]" stays.
    private val ipv6 = Regex("""\[(?=[0-9A-Fa-f:.]*::|(?:[0-9A-Fa-f.]*:){6})[0-9A-Fa-f:.]+(?:%[A-Za-z0-9_.-]+)?]""")
    private val queryValue = Regex("""([?&][A-Za-z0-9_.%-]+=)[^&#\s"'<>]+""")

    /** Addresses that name no one: this device's loopback and the any-address. */
    private val harmless = setOf("127.0.0.1", "0.0.0.0", "[::1]", "[::]", "[0:0:0:0:0:0:0:1]", "[0:0:0:0:0:0:0:0]")

    /** Registers [value] as a [kind]. From now on, the log shows its placeholder instead. */
    fun register(kind: Kind, value: String?) {
        val v = value?.trim().orEmpty()
        if (v.length < MIN_LENGTH) return
        synchronized(lock) {
            if (v in placeholders) return
            placeholders[v] = next(kind.tag)
            matcher = null
        }
    }

    /** [text] with every registered value, IP address and URL query value replaced. */
    fun redact(text: String): String = synchronized(lock) {
        var out = text
        if (placeholders.isNotEmpty()) {
            val regex = matcher ?: build().also { matcher = it }
            out = regex.replace(out) { placeholders[it.value] ?: it.value }
        }
        out = ipv4.replace(out) { m -> if (m.value in harmless) m.value else addressPlaceholder(m.value) }
        out = ipv6.replace(out) { m -> if (m.value in harmless) m.value else addressPlaceholder(m.value) }
        queryValue.replace(out) { it.groupValues[1] + "…" }
    }

    /** Forgets every value. Only tests call it. */
    fun clearForTesting() = synchronized(lock) {
        placeholders.clear()
        counters.clear()
        matcher = null
    }

    private fun addressPlaceholder(address: String): String =
        placeholders.getOrPut(address) { next("ip") }

    private fun next(tag: String): String {
        val n = (counters[tag] ?: 0) + 1
        counters[tag] = n
        return "<$tag-$n>"
    }

    /** One pattern for every value, longest first, so a name inside a longer one does not win. */
    private fun build(): Regex {
        val values = placeholders.keys.filter { !it.startsWith("[") && !ipv4.matches(it) }.sortedByDescending { it.length }
        if (values.isEmpty()) return Regex("(?!)")
        return Regex("""(?<![A-Za-z0-9])(?:""" + values.joinToString("|") { Regex.escape(it) } + """)(?![A-Za-z0-9])""")
    }
}
