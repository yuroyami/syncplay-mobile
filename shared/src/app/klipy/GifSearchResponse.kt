package app.klipy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KlipySearchResponse(
    val result: Boolean = false,
    val data: KlipySearchWrapper = KlipySearchWrapper()
)

@Serializable
data class KlipySearchWrapper(
    val data: List<KlipyItem> = emptyList(),

    @SerialName("current_page")
    val currentPage: Int = 1,
    @SerialName("per_page")
    val perPage: Int = 0,
    @SerialName("has_next")
    val hasNext: Boolean = false
)

/* Every field has a default, so the parser accepts sparse responses. The response shape of
 * Klipy differs a little between the search, trending and recent endpoints. A missing field
 * without a default makes the whole parse fail. */
@Serializable
data class KlipyItem(
    val id: Long = 0L,
    val slug: String = "",
    val title: String = "",
    val file: KlipyFile = KlipyFile(),
    val tags: List<String> = emptyList(),
    val type: String = "",
    @SerialName("blur_preview")
    val blurPreview: String = ""
)

@Serializable
data class KlipyFile(
    val hd: KlipyResolution = KlipyResolution(),
    val md: KlipyResolution = KlipyResolution(),
    val sm: KlipyResolution = KlipyResolution(),
    val xs: KlipyResolution = KlipyResolution()
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