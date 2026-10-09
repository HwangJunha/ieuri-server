package org.around.ieuri.tourism.domain.catalog

/** 카탈로그가 귀속된 시군구. 아직 없으면 [districtCode]가 null이다. [org.around.ieuri.tourism.domain.catalog.spec.DistrictUnassigned]의 후보다. */
data class CatalogDistrict(
    val catalogId: Long,
    val districtCode: String?,
)
