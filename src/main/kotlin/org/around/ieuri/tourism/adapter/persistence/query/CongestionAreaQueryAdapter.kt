package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.application.shared.provided.dto.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.shared.required.CongestionAreaQueryPort
import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapBounds
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.ResultSet

/** 서울 구역 메타데이터 조회 어댑터(SPEC-0014 R6·R16·R17). `boundary`가 geography라 JPA 대신 생 SQL이다. */
@Component
class CongestionAreaQueryAdapter(
    private val exposedJdbcTemplate: JdbcTemplate,
) : CongestionAreaQueryPort {

    /** 121곳 전체의 경계와 메타데이터(R17). 폴리곤은 `ST_AsGeoJSON`의 좌표 배열(`[lng, lat]`)로 뽑는다. */
    override fun findAllBoundaries(locale: DomainLocale): List<CongestionAreaBoundaryView> = exposedJdbcTemplate.query(
        """
            SELECT area_cd, category, name_ko, name_en, name_ja, name_zh,
                   ST_AsGeoJSON(boundary) AS boundary_json
            FROM congestion_area
            ORDER BY area_cd
        """.trimIndent(),
    ) { rs, _ ->
        CongestionAreaBoundaryView(
            id = rs.getString("area_cd"),
            name = rs.areaName().resolve(locale),
            category = rs.getString("category"),
            polygon = parseOuterRing(rs.getString("boundary_json")),
        )
    }

    /**
     * bbox에 걸치는 구역 코드(R6). 폴리곤이 뷰포트와 겹치면 포함이다.
     * - `&&`로 인덱스를 태우고 `ST_Intersects`로 정확히 판정한다.
     */
    override fun findAreaCodesWithin(bounds: MapBounds): List<String> = exposedJdbcTemplate.query(
        """
            SELECT area_cd FROM congestion_area
            WHERE boundary && ST_MakeEnvelope(?, ?, ?, ?, 4326)::geography
              AND ST_Intersects(boundary, ST_MakeEnvelope(?, ?, ?, ?, 4326)::geography)
            ORDER BY area_cd
        """.trimIndent(),
        { rs, _ -> rs.getString("area_cd") },
        // envelope은 (minLng, minLat, maxLng, maxLat) 순서다 — 두 조건에 각각 넘긴다.
        bounds.minLng, bounds.minLat, bounds.maxLng, bounds.maxLat,
        bounds.minLng, bounds.minLat, bounds.maxLng, bounds.maxLat,
    )

    /** 실재하는 구역의 이름만. 없는 코드는 빠지므로 호출자가 부재를 판정한다(R6). */
    override fun findNamesByCodes(areaCodes: List<String>): Map<String, AreaName> {
        if (areaCodes.isEmpty()) return emptyMap()

        val placeholders = areaCodes.joinToString(",") { "?" }
        return exposedJdbcTemplate.query(
            """
            SELECT area_cd, name_ko, name_en, name_ja, name_zh
            FROM congestion_area
            WHERE area_cd IN ($placeholders)
            """.trimIndent(),
            { rs, _ -> rs.getString("area_cd") to rs.areaName() },
            *areaCodes.toTypedArray(),
        ).toMap()
    }

    private fun ResultSet.areaName() = AreaName(
        ko = getString("name_ko"),
        en = getString("name_en"),
        ja = getString("name_ja"),
        zh = getString("name_zh"),
    )

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
    }
}
