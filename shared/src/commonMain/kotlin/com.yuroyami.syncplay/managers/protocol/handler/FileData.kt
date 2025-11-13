package com.yuroyami.syncplay.managers.protocol.handler

import kotlinx.serialization.Serializable

/**
 * Metadata about a media file coming from the server
 *
 * Used in Syncplay protocol messages to describe the currently loaded file.
 */
@Serializable
data class FileData(
    /** File name (with extension). */
    val name: String? = null,

    /** File duration in seconds. */
    val duration: Double? = null,

    /** File size in bytes. */
    val size: Long? = null
)
