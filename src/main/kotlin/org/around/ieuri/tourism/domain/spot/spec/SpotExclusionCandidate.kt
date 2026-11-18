package org.around.ieuri.tourism.domain.spot.spec

import org.around.ieuri.tourism.domain.category.SpotCategory

/** 비관광 제외 판정([SpotExclusionRule])의 후보 — 수집한 원본의 제목과 카테고리. */
data class SpotExclusionCandidate(
    val title: String,
    val category: SpotCategory,
)
