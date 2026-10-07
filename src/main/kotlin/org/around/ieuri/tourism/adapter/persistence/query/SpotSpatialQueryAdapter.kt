package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.common.persistence.geographyPoint
import org.around.ieuri.common.persistence.stDWithin
import org.around.ieuri.common.persistence.stDistance
import org.around.ieuri.tourism.adapter.persistence.mapper.toSpotSearchResult
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.application.shared.required.NearbySpotSearch
import org.around.ieuri.tourism.application.shared.required.SpotSearchResult
import org.around.ieuri.tourism.application.shared.required.SpotSpatialQueryPort
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.allFrom
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

/**
 * 반경 조회 어댑터. `spot`을 읽지 않고 번역·카탈로그만 본다(SPEC-0019).
 * - 조회와 카운트가 같은 조건이어야 한다 — 갈리면 hasNext와 totalCount가 어긋난다.
 * - 거리 정렬은 번역 행 단독으로 하고, 썸네일·저작권만 `LIMIT` 뒤에 카탈로그와 조인한다(SPEC-0038).
 */
@Component
class SpotSpatialQueryAdapter : SpotSpatialQueryPort {

    /** 반경 안 관광지 한 페이지. 거리 오름차순, 등거리는 id 오름차순이다(POL-019·055). */
    override fun searchNearby(query: NearbySpotSearch): List<SpotSearchResult> {
        val t = SpotCatalogTranslationTable
        val distance = stDistance(t.location, center(query)).alias("distance_meters")
        val ranked = t.select(t.catalogId, t.title, t.address, t.mapX, t.mapY, t.category, distance)
            .where { nearby(query) }
            .orderBy(distance.delegate to SortOrder.ASC, t.catalogId to SortOrder.ASC)
            .limit(query.limit)
            .offset(query.offset.toLong())
            .alias("ranked")
        val rankedDistance = ranked[distance]
        return ranked.join(SpotCatalogTable, JoinType.INNER, ranked[t.catalogId], SpotCatalogTable.id)
            .select(
                ranked[t.catalogId], ranked[t.title], ranked[t.address], ranked[t.mapX], ranked[t.mapY], ranked[t.category], rankedDistance,
                SpotCatalogTable.thumbnailImageUrl, SpotCatalogTable.copyright,
            )
            .orderBy(rankedDistance to SortOrder.ASC, ranked[t.catalogId] to SortOrder.ASC)
            .map { it.toSpotSearchResult(ranked, distanceMeters = it[rankedDistance]) }
    }

    /** 카탈로그를 조인하지 않아도 수가 같다 — UNIQUE(catalog_id, language)와 catalog_id NOT NULL FK에 기댄다. */
    override fun countNearby(query: NearbySpotSearch): Int = SpotCatalogTranslationTable.selectAll().where { nearby(query) }.count().toInt()

    /**
     * 조회와 카운트가 함께 쓰는 조건. 번역의 언어로 거른다(SPEC-0019 R-9).
     * - 반경 필터는 `ST_DWithin`이어야 한다 — `ST_Distance(...) <= radius`는 GiST 인덱스를 못 탄다.
     * - 이미 반응한 카탈로그는 DB 단계에서 뺀다 — 조회 후 거르면 페이지가 size보다 작아진다.
     */
    private fun nearby(query: NearbySpotSearch): Op<Boolean> {
        val t = SpotCatalogTranslationTable
        var condition = (t.visible eq true) and (t.language eq query.language.name) and
            stDWithin(t.location, center(query), query.radiusMeters.toDouble())
        // 다중 선택은 OR(SPEC-0020). 비어 있으면 조건을 걸지 않는다.
        if (!query.categories.isUnfiltered) condition = condition and (t.category inList query.categories.codes())
        if (query.excludedSpotIds.isNotEmpty()) condition = condition and (t.catalogId neq allFrom(query.excludedSpotIds.toList()))
        return condition
    }

    /** 요청 좌표. `geographyPoint`는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
    private fun center(query: NearbySpotSearch) = geographyPoint(query.center.longitude, query.center.latitude)
}
