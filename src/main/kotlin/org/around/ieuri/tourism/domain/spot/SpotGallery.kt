package org.around.ieuri.tourism.domain.spot

/** 원본에 딸린 갤러리 전량([Spot.showcase]로만 만든다). 순서가 곧 표시 순서다. */
data class SpotGallery(
    val spotId: Long,
    val images: List<SpotImage>,
)
