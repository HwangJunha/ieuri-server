package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod

/** 카탈로그에 붙은 원본 하나의 축제 기간. 카탈로그는 이 중 하나를 고른다([SpotCatalog.festivalPeriodOf]). */
data class OriginFestivalPeriod(
    val spotId: Long,
    val language: DomainLocale,
    val period: FestivalPeriod,
)
