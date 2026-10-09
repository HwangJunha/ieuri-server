package org.around.ieuri.tourism.domain.catalog.spec

import org.around.ieuri.tourism.domain.catalog.CatalogDistrict
import org.around.ieuri.tourism.domain.shared.Specification

/** 아직 시군구에 귀속되지 않은 카탈로그(SPEC-0030 R11). 한 번 귀속되면 다시 정하지 않는다. */
object DistrictUnassigned : Specification<CatalogDistrict> {
    override fun isSatisfiedBy(candidate: CatalogDistrict): Boolean = candidate.districtCode == null
}
