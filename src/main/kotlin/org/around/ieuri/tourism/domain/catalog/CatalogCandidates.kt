package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.catalog.spec.HasDisplayTitle
import org.around.ieuri.tourism.domain.catalog.spec.SameLocationAndThumbnail

/**
 * 미배정 원본이 붙을 수 있는 카탈로그 후보(SPEC-0019 R-2). 배정 회차 시작 때 읽은 KO 카탈로그로 만든다.
 * - 제목 키로 먼저 찾고, 못 찾으면 썸네일로 내려간다(썸네일 맵은 비한국어 배정에만 준다).
 * - 같은 제목 키 안에서는 딱 하나만 남을 때 붙는다: 비한국어는 후보 자체가 하나면, 아니면 [SameLocationAndThumbnail]을 만족하는 것이 하나면.
 * - 같은 회차에서 새로 만든 KO 카탈로그도 다음 원본의 후보다([withFounded]).
 */
class CatalogCandidates(
    private val titleCandidates: Map<String, List<CatalogCandidate>>,
    private val thumbnailCatalogIds: Map<String, Long> = emptyMap(),
) {

    fun assign(origin: CatalogOrigin): CatalogAssignment {
        if (!HasDisplayTitle.isSatisfiedBy(origin)) return CatalogAssignment.Skip

        val sameTitle = origin.matchKey?.let { titleCandidates[it] }.orEmpty()
        val matched = sameTitle.pickFor(origin) ?: thumbnailCatalogIds[origin.thumbnailImageUrl]
        return if (matched != null) CatalogAssignment.Join(matched) else CatalogAssignment.Found(SpotCatalog.found(origin))
    }

    /** [origin]으로 만든 카탈로그를 후보에 더한다. KO만 자기들끼리 접는다 — 비한국어는 KO에 붙거나 독립한다. */
    fun withFounded(origin: CatalogOrigin, catalogId: Long): CatalogCandidates {
        val key = origin.matchKey
        if (!origin.language.isKorean || key == null) return this
        val candidate = CatalogCandidate(catalogId, origin.mapX, origin.mapY, origin.thumbnailImageUrl)
        return CatalogCandidates(titleCandidates + (key to titleCandidates[key].orEmpty() + candidate), thumbnailCatalogIds)
    }

    /** 일치가 없거나 둘 이상이면 null이다(새 카탈로그를 만든다). */
    private fun List<CatalogCandidate>.pickFor(origin: CatalogOrigin): Long? {
        if (isEmpty()) return null
        if (!origin.language.isKorean) singleOrNull()?.let { return it.catalogId }
        return singleOrNull(SameLocationAndThumbnail(origin)::isSatisfiedBy)?.catalogId
    }
}
