package org.around.ieuri.tourism.domain.catalog

/**
 * 미배정 원본이 붙을 카탈로그를 고른다(SPEC-0019 R-2). 배정 회차 시작 때 읽은 KO 카탈로그로 만든다.
 * - 제목 키로 먼저 찾고, 못 찾으면 썸네일로 내려간다(썸네일 맵은 비한국어 배정에만 준다).
 * - KO는 좌표와 썸네일이 모두 같아야 접는다. 비한국어는 후보가 하나면 좌표를 묻지 않는다.
 * - 같은 회차에서 새로 만든 KO 카탈로그도 다음 원본의 후보다([withFounded]).
 */
class CatalogIndex(
    private val titleCandidates: Map<String, List<CatalogCandidate>>,
    private val thumbnailCatalogIds: Map<String, Long> = emptyMap(),
) {

    fun assign(origin: CatalogOrigin): CatalogAssignment {
        // 표시할 제목이 없으면 미배정으로 남긴다 — 상류가 제대로 된 제목을 주는 날 배정된다.
        if (origin.displayTitle == null) return CatalogAssignment.Skip

        val candidates = origin.matchKey?.let { titleCandidates[it] }.orEmpty()
        val matched = candidates.pickFor(origin) ?: thumbnailCatalogIds[origin.thumbnailImageUrl]
        return if (matched != null) CatalogAssignment.Join(matched) else CatalogAssignment.Found(SpotCatalog.found(origin))
    }

    /** [origin]으로 만든 카탈로그를 후보에 더한다. KO만 자기들끼리 접는다 — 비한국어는 KO에 붙거나 독립한다. */
    fun withFounded(origin: CatalogOrigin, catalogId: Long): CatalogIndex {
        val key = origin.matchKey
        if (!origin.language.isKorean || key == null) return this
        val candidate = CatalogCandidate(catalogId, origin.mapX, origin.mapY, origin.thumbnailImageUrl)
        return CatalogIndex(titleCandidates + (key to titleCandidates[key].orEmpty() + candidate), thumbnailCatalogIds)
    }

    /** 일치가 없거나 둘 이상이면 null이다(새 카탈로그를 만든다). */
    private fun List<CatalogCandidate>.pickFor(origin: CatalogOrigin): Long? {
        if (isEmpty()) return null
        if (!origin.language.isKorean) singleOrNull()?.let { return it.catalogId }
        return singleOrNull { it.mapX == origin.mapX && it.mapY == origin.mapY && it.thumbnailImageUrl == origin.thumbnailImageUrl }?.catalogId
    }
}

/** 같은 제목 키를 가진 KO 카탈로그 후보. 좌표·썸네일로 동명이인과 같은 건물의 다른 매장을 가른다. */
data class CatalogCandidate(
    val catalogId: Long,
    val mapX: Double,
    val mapY: Double,
    val thumbnailImageUrl: String,
)

/** 원본 하나의 배정 판정. */
sealed interface CatalogAssignment {
    /** 기존 카탈로그에 붙는다. */
    data class Join(val catalogId: Long) : CatalogAssignment

    /** 새 카탈로그를 만든다. */
    data class Found(val catalog: SpotCatalog) : CatalogAssignment

    /** 표시할 제목이 없어 미배정으로 남긴다. */
    data object Skip : CatalogAssignment
}
