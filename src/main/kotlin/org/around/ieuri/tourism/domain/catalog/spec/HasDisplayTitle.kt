package org.around.ieuri.tourism.domain.catalog.spec

import org.around.ieuri.tourism.domain.catalog.CatalogOrigin
import org.around.ieuri.tourism.domain.shared.Specification

/** 원본에 표시할 제목이 있는가(SPEC-0019 R-2). 없으면 배정하지 않는다 — 상류가 제대로 된 제목을 주는 날 배정된다. */
object HasDisplayTitle : Specification<CatalogOrigin> {
    override fun isSatisfiedBy(candidate: CatalogOrigin): Boolean = candidate.displayTitle != null
}
