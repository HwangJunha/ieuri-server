package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.common.persistence.asGeography
import org.around.ieuri.common.persistence.boxOverlaps
import org.around.ieuri.common.persistence.envelope
import org.around.ieuri.common.persistence.geographyPoint
import org.around.ieuri.common.persistence.knn
import org.around.ieuri.common.persistence.stDistance
import org.around.ieuri.tourism.adapter.persistence.mapper.toMapCandidateView
import org.around.ieuri.tourism.adapter.persistence.mapper.toMapSpotView
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.application.catalog.provided.dto.MapSpotView
import org.around.ieuri.tourism.application.catalog.required.MapQueryPort
import org.around.ieuri.tourism.application.catalog.required.dto.MapCandidateSearch
import org.around.ieuri.tourism.application.catalog.required.dto.MapCandidateView
import org.around.ieuri.tourism.application.catalog.required.dto.MapViewportSearch
import org.around.ieuri.tourism.domain.catalog.CategorySelection
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapBounds
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.allFrom
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

/**
 * 지도 bbox 조회 어댑터(SPEC-0014 R1·R3·SPEC-0036). `spot`을 읽지 않는다(SPEC-0019). 마커 id는 `spot_catalog.id`다.
 * - **제목만 번역에서 온다.** 좌표·썸네일·분류는 카탈로그 표준값이다. `UNIQUE(catalog_id, language)`라 조인은 카탈로그당 최대 1행이다.
 */
@Component
class MapSpatialQueryAdapter : MapQueryPort {

    /** bbox 안의 개별 마커. id 오름차순 — 상한에 걸려 잘릴 때 매 요청 같은 집합이 나온다. */
    override fun findSpotsWithin(query: MapViewportSearch): List<MapSpotView> = visibleIn(query.language)
        .select(MARKER_COLUMNS)
        .where { withinBox(query.bounds) and inCategories(query.categories) }
        .orderBy(SpotCatalogTable.id, SortOrder.ASC)
        .limit(query.limit)
        .map { it.toMapSpotView() }

    /**
     * bbox 안의 추천 후보. 중심점에서 가까운 순 상한 N건이며 최종 순서는 호출자가 점수로 정한다.
     * - 정렬은 `<->`(인덱스 KNN), 표시용 거리는 `ST_Distance`로 따로 계산한다.
     */
    override fun findRecommendationCandidatesWithin(query: MapCandidateSearch): List<MapCandidateView> {
        val center = geographyPoint(query.bounds.center.longitude, query.bounds.center.latitude)
        val distance = stDistance(SpotCatalogTable.location, center)
        return visibleIn(query.language)
            .select(MARKER_COLUMNS + distance)
            .where {
                var condition = withinBox(query.bounds) and inCategories(query.categories)
                // 제외 id는 배열 한 개로 바인딩한다.
                if (query.excludedSpotIds.isNotEmpty()) condition = condition and (SpotCatalogTable.id neq allFrom(query.excludedSpotIds.toList()))
                condition
            }
            .orderBy(SpotCatalogTable.location knn center to SortOrder.ASC, SpotCatalogTable.id to SortOrder.ASC)
            .limit(query.limit)
            .map { it.toMapCandidateView(distanceMeters = it[distance]) }
    }

    private fun visibleIn(language: DomainLocale) = SpotCatalogTable.join(SpotCatalogTranslationTable, JoinType.INNER, SpotCatalogTranslationTable.catalogId, SpotCatalogTable.id) {
        (SpotCatalogTranslationTable.language eq language.name) and (SpotCatalogTranslationTable.visible eq true)
    }

    /**
     * bbox 교차 판정. 경계선에 걸친 점을 포함한다(R1).
     * - envelope을 geography로 캐스팅해야 GiST 인덱스를 탄다. 컬럼을 geometry로 캐스팅하면 전 행 필터가 된다.
     */
    private fun withinBox(bounds: MapBounds): Op<Boolean> = SpotCatalogTable.location boxOverlaps envelope(bounds.minLng, bounds.minLat, bounds.maxLng, bounds.maxLat).asGeography()

    /** 다중 선택은 OR(SPEC-0020). 비어 있으면 조건을 걸지 않는다. */
    private fun inCategories(categories: CategorySelection): Op<Boolean> = if (categories.isUnfiltered) Op.TRUE else SpotCatalogTable.category inList categories.codes()

    private companion object {
        val MARKER_COLUMNS = listOf(
            SpotCatalogTable.id,
            SpotCatalogTranslationTable.title,
            SpotCatalogTable.thumbnailImageUrl,
            SpotCatalogTable.category,
            SpotCatalogTable.mapX,
            SpotCatalogTable.mapY,
        )
    }
}
