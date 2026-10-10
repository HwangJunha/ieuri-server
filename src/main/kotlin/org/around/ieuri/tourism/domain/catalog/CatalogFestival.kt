package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.FestivalPeriod

/** 카탈로그의 축제 기간([SpotCatalog.festivalFrom]). [period]가 null이면 축제 원본이 없다 — 기간을 지운다. */
data class CatalogFestival(
    val catalogId: Long,
    val period: FestivalPeriod?,
)
