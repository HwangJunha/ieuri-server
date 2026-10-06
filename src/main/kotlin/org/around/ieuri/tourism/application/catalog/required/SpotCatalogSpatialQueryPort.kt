package org.around.ieuri.tourism.application.catalog.required

import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.SpotCategory

/** 카탈로그 분할 후보를 찾는 읽기 포트(SPEC-0029). PostGIS 거리가 필요해 생 SQL 포트로 갈린다. */
interface SpotCatalogSpatialQueryPort {

    /**
     * 서로 다른 장소가 한 카탈로그로 접힌 것으로 보이는 카탈로그 id. 두 조건을 함께 만족해야 한다.
     *
     * @param minOriginCount 이 수를 **초과**하는 원본을 가진 카탈로그만 본다.
     * @param minDistanceMeters 원본 간 최대 거리가 이 값 **이상**이어야 후보다.
     */
    fun findSplitCandidates(minOriginCount: Int, minDistanceMeters: Double): List<Long>

    /**
     * 한 카탈로그에 매달린 원본 전부.
     * - 좌표가 유효한 행만 온다.
     */
    fun findOrigins(catalogId: Long): List<CatalogOrigin>
}

/** 카탈로그에 매달린(이미 배정된) 원본 spot 한 건. */
data class CatalogOrigin(
    val spotId: Long,
    val language: DomainLocale,
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val mapX: Double,
    val mapY: Double,
    val category: SpotCategory,
    val copyright: CopyrightType,
)
