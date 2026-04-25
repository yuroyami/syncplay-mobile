package app.protocol

import app.utils.ProtocolApi
import kotlinx.serialization.json.Json

/**
 * Shared `Json` instance used for both client and server encode/decode.
 *
 * - `ignoreUnknownKeys` — gracefully tolerate fields added by future protocol revisions.
 * - `coerceInputValues` — coerce JSON `null` to default values for non-nullable params.
 * - `encodeDefaults` — keep default-valued fields in the output (e.g. empty `motd`,
 *   `controllerAuth.password = ""`) so the wire format stays byte-compatible with the
 *   reference Python server.
 * - `explicitNulls = false` — drop `null` fields from output, matching how the Python
 *   server omits absent keys.
 */
@ProtocolApi
val syncplayJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}
