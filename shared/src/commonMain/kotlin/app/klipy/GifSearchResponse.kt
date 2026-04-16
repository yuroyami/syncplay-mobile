package app.klipy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KlipySearchResponse(
    val result: Boolean,
    val data: KlipySearchWrapper
)

@Serializable
data class KlipySearchWrapper(
    val data: List<KlipyItem>,

    @SerialName("current_page")
    val currentPage: Int,
    @SerialName("per_page")
    val perPage: Int,
    @SerialName("has_next")
    val hasNext: Boolean
)

@Serializable
data class KlipyItem(
    val id: Long,
    val slug: String,
    val title: String,
    val file: KlipyFile,
    val tags: List<String>,
    val type: String,
    @SerialName("blur_preview")
    val blurPreview: String
)

@Serializable
data class KlipyFile(
    val hd: KlipyResolution,
    val md: KlipyResolution,
    val sm: KlipyResolution,
    val xs: KlipyResolution
)

@Serializable
data class KlipyResolution(
    val gif: KlipyFormat = KlipyFormat(),
    val webp: KlipyFormat = KlipyFormat(),
    val webm: KlipyFormat = KlipyFormat(),
    val mp4: KlipyFormat = KlipyFormat(),
    val jpg: KlipyFormat = KlipyFormat(),
    val png: KlipyFormat = KlipyFormat()
)

@Serializable
data class KlipyFormat(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val size: Int = 0
)