package org.around.ieuri.tourism.domain.spot

import org.around.ieuri.tourism.domain.category.SpotCategory

/** 원본에 딸린, [category]로 읽은 운영 정보([Spot.introduce]로만 만든다). [intro]가 null이면 "물었고 없었다"다. */
data class SpotIntroduction(
    val spotId: Long,
    val category: SpotCategory,
    val intro: SpotIntro?,
)
