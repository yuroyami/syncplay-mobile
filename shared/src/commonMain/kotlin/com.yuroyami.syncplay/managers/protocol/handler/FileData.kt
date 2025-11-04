package com.yuroyami.syncplay.managers.protocol.handler

import kotlinx.serialization.Serializable

@Serializable
data class FileData(
    val name: String? = null,
    val duration: Double? = null,
    val size: Long? = null
)