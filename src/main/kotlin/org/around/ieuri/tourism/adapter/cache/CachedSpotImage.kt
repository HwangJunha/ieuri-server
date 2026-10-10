package org.around.ieuri.tourism.adapter.cache

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.spot.SpotImage
@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedSpotImage(
    val imageUrl: String = "",
    val thumbnailUrl: String? = null,
    val caption: String? = null,
    val copyright: CopyrightType = CopyrightType.UNSPECIFIED,
) {
    fun toDomain(): SpotImage = SpotImage(
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl,
        caption = caption,
        copyright = copyright,
    )

    companion object {
        fun from(image: SpotImage): CachedSpotImage = CachedSpotImage(
            imageUrl = image.imageUrl,
            thumbnailUrl = image.thumbnailUrl,
            caption = image.caption,
            copyright = image.copyright,
        )
    }
}
