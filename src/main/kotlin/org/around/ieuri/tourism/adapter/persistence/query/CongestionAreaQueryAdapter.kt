package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.common.persistence.asGeography
import org.around.ieuri.common.persistence.boxOverlaps
import org.around.ieuri.common.persistence.envelope
import org.around.ieuri.common.persistence.stAsGeoJson
import org.around.ieuri.common.persistence.stIntersects
import org.around.ieuri.tourism.adapter.persistence.mapper.toCongestionAreaName
import org.around.ieuri.tourism.adapter.persistence.table.CongestionAreaTable
import org.around.ieuri.tourism.application.congestion.provided.dto.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.congestion.required.CongestionAreaQueryPort
import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapBounds
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

/** 서울 구역 메타데이터 조회 어댑터(SPEC-0014 R6·R16·R17). */
@Component
class CongestionAreaQueryAdapter : CongestionAreaQueryPort {

    /** 121곳 전체의 경계와 메타데이터(R17). 폴리곤은 `ST_AsGeoJSON`의 좌표 배열(`[lng, lat]`)로 뽑는다. */
    override fun findAllBoundaries(locale: DomainLocale): List<CongestionAreaBoundaryView> {
        val boundaryJson = stAsGeoJson(CongestionAreaTable.boundary)
        return CongestionAreaTable.select(NAME_COLUMNS + CongestionAreaTable.category + boundaryJson)
            .orderBy(CongestionAreaTable.areaCd, SortOrder.ASC)
            .map {
                CongestionAreaBoundaryView(
                    id = it[CongestionAreaTable.areaCd],
                    name = it.toCongestionAreaName().resolve(locale),
                    category = it[CongestionAreaTable.category],
                    polygon = parseOuterRing(it[boundaryJson]),
                )
            }
    }

    /**
     * bbox에 걸치는 구역 코드(R6). 폴리곤이 뷰포트와 겹치면 포함이다.
     * - `&&`로 인덱스를 태우고 `ST_Intersects`로 정확히 판정한다. envelope은 (minLng, minLat, maxLng, maxLat) 순서다.
     */
    override fun findAreaCodesWithin(bounds: MapBounds): List<String> {
        val box = envelope(bounds.minLng, bounds.minLat, bounds.maxLng, bounds.maxLat).asGeography()
        return CongestionAreaTable.select(CongestionAreaTable.areaCd)
            .where { (CongestionAreaTable.boundary boxOverlaps box) and stIntersects(CongestionAreaTable.boundary, box) }
            .orderBy(CongestionAreaTable.areaCd, SortOrder.ASC)
            .map { it[CongestionAreaTable.areaCd] }
    }

    /** 실재하는 구역의 이름만. 없는 코드는 빠지므로 호출자가 부재를 판정한다(R6). */
    override fun findNamesByCodes(areaCodes: List<String>): Map<String, AreaName> {
        if (areaCodes.isEmpty()) return emptyMap()

        return CongestionAreaTable.select(NAME_COLUMNS)
            .where { CongestionAreaTable.areaCd inList areaCodes }
            .associate { it[CongestionAreaTable.areaCd] to it.toCongestionAreaName() }
    }

    /** GeoJSON Polygon → 외곽 링 `[[lng, lat], ...]`. 첫 번째 링만 취한다(홀 무시). */
    private fun parseOuterRing(geoJson: String): List<List<Double>> {
        val coordinates = MAPPER.readTree(geoJson).get("coordinates") ?: return emptyList()
        val outerRing = coordinates.firstOrNull() ?: return emptyList()
        return buildList {
            for (point in outerRing) {
                add(listOf(point.get(0).asDouble(), point.get(1).asDouble()))
            }
        }
    }

    private companion object {
        val MAPPER = tools.jackson.databind.json.JsonMapper.builder().build()

        val NAME_COLUMNS = listOf(
            CongestionAreaTable.areaCd,
            CongestionAreaTable.nameKo,
            CongestionAreaTable.nameEn,
            CongestionAreaTable.nameJa,
            CongestionAreaTable.nameZh,
        )
    }
}
