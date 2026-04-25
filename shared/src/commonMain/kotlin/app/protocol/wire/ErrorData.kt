package app.protocol.wire

import kotlinx.serialization.Serializable

/** Symmetric error payload — either side may send `{"Error": {"message": "..."}}`. */
@Serializable
data class ErrorData(
    val message: String? = null
)
