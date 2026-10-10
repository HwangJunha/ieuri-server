package org.around.ieuri.tourism.application.spot

import org.around.ieuri.tourism.domain.spot.SpotImage

data class SpotGalleryFetched(
    val spotId: Long,
    val gallery: List<SpotImage>,
)
