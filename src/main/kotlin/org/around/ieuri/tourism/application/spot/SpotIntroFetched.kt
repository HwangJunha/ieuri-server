package org.around.ieuri.tourism.application.spot

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.spot.SpotIntro

/** [intro]는 정상적으로 null일 수 있다 — "값 없음"도 적재한다. */
data class SpotIntroFetched(
    val spotId: Long,
    val category: SpotCategory,
    val intro: SpotIntro?,
)
