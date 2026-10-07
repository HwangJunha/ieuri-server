package org.around.ieuri.tourism.application.spot.required.dto

import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 상세 조회 입력. */
data class SpotDetailQuery(
    val contentId: String,
    val locale: DomainLocale,
)
