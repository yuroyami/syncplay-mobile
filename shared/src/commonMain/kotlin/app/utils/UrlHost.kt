package app.utils

/**
 * The host that a URL really names, read the same way as Python's `urlparse().hostname`.
 *
 * That reading is the one that matters when someone else wrote the string. The user info before
 * an `@` is not the host, an IPv6 literal loses its brackets, the port is dropped, and the rest
 * is lower case. Returns null when there is no usable host. A caller that decides whether to
 * trust a URL must treat null as "do not trust".
 *
 * `https://klipy.com:x@evil.example/a.gif` names `evil.example`. A cut at the first colon would
 * name `klipy.com` and auto-load the image.
 */
fun urlHost(url: String): String? {
    val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
    if (afterScheme.isEmpty()) return null
    val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
    val hostPort = authority.substringAfterLast('@')
    val host = if (hostPort.startsWith("[")) {
        hostPort.substringAfter('[').substringBefore(']')
    } else {
        hostPort.substringBefore(':')
    }
    return host.lowercase().takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) }
}

/**
 * The path of a URL without the query or fragment, or "" when there is no path.
 *
 * A trusted-domain entry can carry a path prefix, and the prefix is matched against this path.
 * The query is dropped because the reference client compares against `urlparse().path`, which
 * has no query either.
 */
fun urlPath(url: String): String {
    val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
    val slash = afterScheme.indexOf('/')
    val stop = afterScheme.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) afterScheme.length else it }
    return if (slash < 0 || slash > stop) "" else afterScheme.substring(slash, stop)
}
