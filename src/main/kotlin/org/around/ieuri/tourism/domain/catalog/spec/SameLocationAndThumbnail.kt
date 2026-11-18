package org.around.ieuri.tourism.domain.catalog.spec

import org.around.ieuri.tourism.domain.catalog.CatalogCandidate
import org.around.ieuri.tourism.domain.catalog.CatalogOrigin
import org.around.ieuri.tourism.domain.shared.Specification

/** 후보 카탈로그가 [origin]과 좌표·썸네일이 모두 같은가(SPEC-0019 R-2). 같은 제목 키의 후보를 같은 장소로 접는 조건이다. */
class SameLocationAndThumbnail(private val origin: CatalogOrigin) : Specification<CatalogCandidate> {
    override fun isSatisfiedBy(candidate: CatalogCandidate): Boolean = candidate.mapX == origin.mapX && candidate.mapY == origin.mapY && candidate.thumbnailImageUrl == origin.thumbnailImageUrl
}
