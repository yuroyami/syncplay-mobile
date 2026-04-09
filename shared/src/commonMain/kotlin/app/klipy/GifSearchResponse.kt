package app.klipy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GifSearchResponse(
    val result: Boolean,
    val data: GifSearchDataWrapper
)

@Serializable
data class GifSearchDataWrapper(
    val data: List<GifItem>,
    @SerialName("current_page")
    val currentPage: Int,
    @SerialName("per_page")
    val perPage: Int,
    @SerialName("has_next")
    val hasNext: Boolean
)

@Serializable
data class GifItem(
    val id: Long,
    val slug: String,
    val title: String,
    val file: GifFile,
    val tags: List<String>,
    val type: String,
    @SerialName("blur_preview")
    val blurPreview: String
)

@Serializable
data class GifFile(
    val hd: GifResolution,
    val md: GifResolution,
    val sm: GifResolution,
    val xs: GifResolution
)

@Serializable
data class GifResolution(
    val gif: GifFormat,
    val webp: GifFormat,
    val jpg: GifFormat,
    val mp4: GifFormat,
    val webm: GifFormat
)

@Serializable
data class GifFormat(
    val url: String,
    val width: Int,
    val height: Int,
    val size: Int
)