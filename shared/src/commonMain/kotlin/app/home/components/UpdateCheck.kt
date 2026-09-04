package app.home.components

import SyncplayMobile.shared.KiteBuildConfig
import app.utils.httpClient
import app.utils.loggy
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Asks GitHub what the newest release is.
 *
 * The app is offered as a direct download as well as through stores, and a direct download has
 * nothing that tells the user a new version exists. This is that: asked for, never on its own,
 * and it reports rather than downloads anything.
 */
object UpdateCheck {

    private const val LATEST = "https://api.github.com/repos/yuroyami/syncplay-mobile/releases/latest"

    /** What the release page says, or null when the answer could not be read. */
    sealed interface Result {
        /** Nothing newer than what is installed. */
        data object UpToDate : Result

        /** A newer release exists; [version] is its tag and [url] its page. */
        data class Newer(val version: String, val url: String) : Result

        /** The list could not be reached, or made no sense. */
        data object Unreachable : Result
    }

    suspend fun latest(): Result = try {
        val body = httpClient.get(LATEST).bodyAsText()
        val json = Json.parseToJsonElement(body).jsonObject
        val tag = json["tag_name"]?.jsonPrimitive?.content?.removePrefix("v").orEmpty()
        val url = json["html_url"]?.jsonPrimitive?.content.orEmpty()
        when {
            tag.isEmpty() || url.isEmpty() -> Result.Unreachable
            isNewer(tag, KiteBuildConfig.APP_VERSION) -> Result.Newer(tag, url)
            else -> Result.UpToDate
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        loggy("Update check failed: ${e.message}")
        Result.Unreachable
    }

    /**
     * Compares two dotted versions number by number, so 0.24.0 beats 0.9.9 the way it should and
     * a string comparison would not. Anything that is not a number counts as zero.
     */
    internal fun isNewer(candidate: String, installed: String): Boolean {
        val a = candidate.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val b = installed.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}
