package com.yuroyami.syncplay.protocol.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ReceivedJson(
    @SerialName("Hello") val hello: JsonObject? = null,
    @SerialName("Set") val set: JsonObject? = null,
    @SerialName("List") val list: JsonObject? = null,
    @SerialName("State") val state: JsonObject? = null,
    @SerialName("Chat") val chat: JsonObject? = null,
    @SerialName("Error") val error: JsonObject? = null,
    @SerialName("TLS") val tls: JsonObject? = null
)