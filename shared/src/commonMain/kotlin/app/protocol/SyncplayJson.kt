package app.protocol

import app.utils.ProtocolApi
import kotlinx.serialization.json.Json

/**
 * Shared `Json` instance used for both client and server encode/decode.
 *
 * - `ignoreUnknownKeys`: tolerate fields that a newer protocol version adds.
 * - `coerceInputValues`: turn a JSON `null` into the default value of a non-nullable property.
 * - `encodeDefaults`: keep default-valued fields in the output. The `null` body of a
 *   `{"List": null}` request is one, and without this the request would lose its only key.
 * - `explicitNulls = false`: drop `null` fields from the output, matching how the Python
 *   reference client and server leave absent keys out.
 */
@ProtocolApi
val syncplayJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}
